package com.atuy.scomb.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsManager @Inject constructor(@param:ApplicationContext private val context: Context) {

    companion object {
        val NOTIFICATION_TIMINGS_KEY = stringSetPreferencesKey("notification_timings")
        val DEFAULT_TIMINGS = setOf("60")

        val SHOW_HOME_NEWS_KEY = booleanPreferencesKey("show_home_news")
        const val DEFAULT_SHOW_HOME_NEWS = true

        val DEBUG_MODE_KEY = booleanPreferencesKey("debug_mode")
        const val DEFAULT_DEBUG_MODE = false
    }

    val notificationTimingsFlow: Flow<Set<String>> = context.settingsDataStore.data.map { preferences ->
        preferences[NOTIFICATION_TIMINGS_KEY] ?: DEFAULT_TIMINGS
    }

    suspend fun setNotificationTimings(timings: Set<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[NOTIFICATION_TIMINGS_KEY] = timings
        }
    }

    val showHomeNewsFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[SHOW_HOME_NEWS_KEY] ?: DEFAULT_SHOW_HOME_NEWS
    }

    suspend fun setShowHomeNews(show: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SHOW_HOME_NEWS_KEY] = show
        }
    }

    val debugModeFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[DEBUG_MODE_KEY] ?: DEFAULT_DEBUG_MODE
    }

    suspend fun setDebugMode(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEBUG_MODE_KEY] = enabled
        }
    }
}