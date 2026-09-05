package com.cs426.learningmocha.data.prefs

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.cs426.learningmocha.net.ApiClient

/**
 * User preferences: theme, backend gateway URL, backup reminder state.
 * Deliberately SharedPreferences — a handful of scalars that must be readable
 * synchronously during Application.onCreate to apply the theme before any view inflates.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mocha_settings", Context.MODE_PRIVATE)

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    /**
     * Always a URL OkHttp can parse, ending in "/" so Retrofit treats it as a directory
     * base. An address that cannot be parsed is refused rather than stored: keeping the
     * last working gateway beats saving one the app could never reach. The read side
     * normalizes too, so a value written by an older build is repaired instead of
     * failing silently at request time.
     */
    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND, null)?.let { ApiClient.normalizeBaseUrl(it) }
            ?: ApiClient.DEFAULT_BASE_URL
        set(value) {
            val normalized = ApiClient.normalizeBaseUrl(value) ?: return
            prefs.edit().putString(KEY_BACKEND, normalized).apply()
        }

    var backupRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_REMINDERS, value).apply()

    /** 0 when the library has never been exported. Only a real export sets this. */
    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP, value).apply()

    /**
     * When the reminder countdown started — first launch, or the last export.
     * Kept separate from [lastBackupAt] so a user who has never exported is not
     * told they have.
     */
    var reminderClockAt: Long
        get() = prefs.getLong(KEY_REMINDER_CLOCK, 0L)
        set(value) = prefs.edit().putLong(KEY_REMINDER_CLOCK, value).apply()

    fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_BACKEND = "backend_url"
        const val KEY_REMINDERS = "backup_reminders"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_REMINDER_CLOCK = "reminder_clock_at"
    }
}
