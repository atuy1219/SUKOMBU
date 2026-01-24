package com.atuy.scomb.data.manager

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private const val PREF_FILE_NAME = "secure_auth_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_ALIAS = "scomb_master_key"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
    }

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(PREF_FILE_NAME) }
    )

    private val authTokenKey = stringPreferencesKey(KEY_AUTH_TOKEN)
    private val usernameKey = stringPreferencesKey(KEY_USERNAME)

    private fun getOrCreateSecretKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(alias)) {
            val entry = ks.getEntry(alias, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plain: String): String {
        try {
            val key = getOrCreateSecretKey(KEY_ALIAS)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            
            val iv = cipher.iv
            val combined = ByteBuffer.allocate(iv.size + cipherText.size)
                .put(iv)
                .put(cipherText)
                .array()

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    private fun decrypt(encoded: String): String? {
        try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH) return null
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val cipherBytes = combined.copyOfRange(IV_LENGTH, combined.size)
            val key = getOrCreateSecretKey(KEY_ALIAS)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            val plain = cipher.doFinal(cipherBytes)
            return String(plain, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun saveAuthToken(token: String) {
        val enc = encrypt(token)
        dataStore.edit { prefs -> prefs[authTokenKey] = enc }
    }

    suspend fun saveUsername(username: String) {
        val enc = encrypt(username)
        dataStore.edit { prefs -> prefs[usernameKey] = enc }
    }

    val authTokenFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[authTokenKey]?.let { decrypt(it) }
    }

    val usernameFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[usernameKey]?.let { decrypt(it) }
    }

    suspend fun clearAuthToken() {
        dataStore.edit { prefs ->
            prefs.remove(authTokenKey)
            prefs.remove(usernameKey)
        }
    }
}