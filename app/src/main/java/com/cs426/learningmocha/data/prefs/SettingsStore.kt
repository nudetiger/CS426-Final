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

    /**
     * The picked theme, stored as the opaque key of a `ui.common.AppTheme`. It stays a bare
     * String here so that the preference layer does not have to know what themes exist —
     * `AppTheme.of` maps it back, and an unknown key (an older build, a hand-edited file)
     * falls through to the default instead of crashing.
     *
     * Builds before named palettes stored an AppCompatDelegate night mode under a different
     * key; [legacyThemeMode] reads that once so an upgrade keeps the light/dark choice.
     */
    var themeKey: String
        get() = prefs.getString(KEY_THEME_KEY, null) ?: legacyThemeMode()
        set(value) = prefs.edit().putString(KEY_THEME_KEY, value).apply()

    private fun legacyThemeMode(): String =
        when (prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) {
            AppCompatDelegate.MODE_NIGHT_NO -> "mocha_light"
            AppCompatDelegate.MODE_NIGHT_YES -> "mocha_dark"
            else -> "mocha_system"
        }

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

    /**
     * Reading comfort, applied wherever long-form markdown is rendered (the reader, and the
     * editor's preview). Both are multipliers on the design tokens rather than absolute sizes,
     * so a user who has also scaled up their system font still gets the ratio they picked.
     */
    var readerTextScale: Float
        get() = prefs.getFloat(KEY_TEXT_SCALE, 1.0f).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        set(value) = prefs.edit()
            .putFloat(KEY_TEXT_SCALE, value.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE))
            .apply()

    /** Line spacing multiplier for post bodies — the "lines margin" of the appearance screen. */
    var readerLineSpacing: Float
        get() = prefs.getFloat(KEY_LINE_SPACING, 1.5f).coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING)
        set(value) = prefs.edit()
            .putFloat(KEY_LINE_SPACING, value.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING))
            .apply()

    /**
     * Whether Browse tints rows by learning status and stripes alternating ones. On by default:
     * it is the whole point of the colour work. Off is for users who find it noisy.
     */
    var colorfulLists: Boolean
        get() = prefs.getBoolean(KEY_COLORFUL_LISTS, true)
        set(value) = prefs.edit().putBoolean(KEY_COLORFUL_LISTS, value).apply()

    /** Whether the assistant offers to switch modes when a message looks like the wrong one. */
    var suggestChatMode: Boolean
        get() = prefs.getBoolean(KEY_SUGGEST_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_SUGGEST_MODE, value).apply()

    /**
     * Browse sort order, stored by enum name. Kept here rather than in SavedStateHandle so the
     * choice survives the app being killed: an ordering the user picked once should not quietly
     * revert to alphabetical the next morning.
     */
    var browseSort: String
        get() = prefs.getString(KEY_BROWSE_SORT, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_BROWSE_SORT, value).apply()

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

    companion object {
        const val MIN_TEXT_SCALE = 0.85f
        const val MAX_TEXT_SCALE = 1.4f
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.2f

        private const val KEY_THEME = "theme_mode"
        private const val KEY_THEME_KEY = "theme_key"
        private const val KEY_BACKEND = "backend_url"
        private const val KEY_REMINDERS = "backup_reminders"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_REMINDER_CLOCK = "reminder_clock_at"
        private const val KEY_TEXT_SCALE = "reader_text_scale"
        private const val KEY_LINE_SPACING = "reader_line_spacing"
        private const val KEY_COLORFUL_LISTS = "colorful_lists"
        private const val KEY_SUGGEST_MODE = "suggest_chat_mode"
        private const val KEY_BROWSE_SORT = "browse_sort"
    }
}
