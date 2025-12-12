package com.atuy.scomb.data.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

        val DISPLAY_WEEK_DAYS_KEY = stringSetPreferencesKey("display_week_days")
        val DEFAULT_DISPLAY_WEEK_DAYS = setOf("0", "1", "2", "3", "4", "5")

        val TIMETABLE_PERIOD_COUNT_KEY = intPreferencesKey("timetable_period_count")
        const val DEFAULT_TIMETABLE_PERIOD_COUNT = 7

        val THEME_MODE_KEY = intPreferencesKey("theme_mode")
        const val THEME_MODE_SYSTEM = 0
        const val THEME_MODE_LIGHT = 1
        const val THEME_MODE_DARK = 2

        // 自動更新間隔（分）
        val AUTO_REFRESH_INTERVAL_KEY = longPreferencesKey("auto_refresh_interval")
        const val DEFAULT_AUTO_REFRESH_INTERVAL = 60L

        // 最終更新時刻（フォアグラウンド全更新用）
        val LAST_SYNC_TIME_KEY = longPreferencesKey("last_sync_time")
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

    val displayWeekDaysFlow: Flow<Set<Int>> = context.settingsDataStore.data.map { preferences ->
        (preferences[DISPLAY_WEEK_DAYS_KEY] ?: DEFAULT_DISPLAY_WEEK_DAYS)
            .mapNotNull { it.toIntOrNull() }
            .toSet()
    }

    suspend fun setDisplayWeekDays(days: Set<Int>) {
        context.settingsDataStore.edit { preferences ->
            preferences[DISPLAY_WEEK_DAYS_KEY] = days.map { it.toString() }.toSet()
        }
    }

    val timetablePeriodCountFlow: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[TIMETABLE_PERIOD_COUNT_KEY] ?: DEFAULT_TIMETABLE_PERIOD_COUNT
    }

    suspend fun setTimetablePeriodCount(count: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[TIMETABLE_PERIOD_COUNT_KEY] = count
        }
    }

    val themeModeFlow: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: THEME_MODE_SYSTEM
    }

    suspend fun setThemeMode(mode: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    // 自動更新間隔のFlow
    val autoRefreshIntervalFlow: Flow<Long> = context.settingsDataStore.data.map { preferences ->
        preferences[AUTO_REFRESH_INTERVAL_KEY] ?: DEFAULT_AUTO_REFRESH_INTERVAL
    }

    suspend fun setAutoRefreshInterval(minutes: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_REFRESH_INTERVAL_KEY] = minutes
        }
    }

    val lastSyncTimeFlow: Flow<Long> = context.settingsDataStore.data.map { preferences ->
        preferences[LAST_SYNC_TIME_KEY] ?: 0L
    }

    suspend fun updateLastSyncTime(time: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME_KEY] = time
        }
    }
}