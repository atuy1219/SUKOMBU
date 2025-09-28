package com.atuy.scomb.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// DataStoreのインスタンスをContextの拡張として定義
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class SessionManager @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private val SESSION_ID_KEY = stringPreferencesKey("session_id")
    }

    // セッションIDを保存する
    suspend fun saveSessionId(sessionId: String) {
        context.dataStore.edit { preferences ->
            preferences[SESSION_ID_KEY] = sessionId
        }
    }

    // 保存されたセッションIDをFlowとして取得する
    val sessionIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SESSION_ID_KEY]
    }

    suspend fun clearSessionId() {
        context.dataStore.edit { preferences ->
            preferences.remove(SESSION_ID_KEY)
        }
    }
}