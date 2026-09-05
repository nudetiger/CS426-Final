package com.cs426.learningmocha.ui.common

import android.app.Activity
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.cs426.learningmocha.R

/**
 * Every theme the user can pick, and everything the app needs to apply or preview one.
 *
 * A theme is two settings that always travel together: the Activity style that supplies the
 * palette attributes (see `values/attrs_theme.xml`) and the night mode the framework's own
 * widgets follow. Splitting them was the old bug waiting to happen — a dark palette under
 * MODE_NIGHT_NO gets light spinner popups and light text-selection handles — so they are
 * declared once, here, and nothing else is allowed to set one without the other.
 *
 * The three Mocha entries share a style and differ only in night mode, because Mocha is the
 * DayNight palette. The named palettes are fixed dark schemes, so they pin MODE_NIGHT_YES.
 *
 * [key] is what SharedPreferences stores. It is written down rather than derived from [name]
 * so that renaming an entry cannot silently reset everybody's theme.
 */
enum class AppTheme(
    val key: String,
    @StyleRes val styleRes: Int,
    val nightMode: Int,
    @StringRes val labelRes: Int,
    @StringRes val captionRes: Int,
    @ColorRes val previewBackground: Int,
    @ColorRes val previewChrome: Int,
    @ColorRes val previewAccentOne: Int,
    @ColorRes val previewAccentTwo: Int,
    @ColorRes val previewAccentThree: Int,
) {
    /** Mocha, following the phone. Its swatch shows a light body under a dark bar, on purpose. */
    MOCHA_SYSTEM(
        key = "mocha_system",
        styleRes = R.style.Theme_LearningMocha,
        nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        labelRes = R.string.theme_mocha_system,
        captionRes = R.string.theme_mocha_system_caption,
        previewBackground = R.color.preview_mocha_light_bg,
        previewChrome = R.color.preview_mocha_dark_chrome,
        previewAccentOne = R.color.preview_mocha_light_one,
        previewAccentTwo = R.color.preview_mocha_light_two,
        previewAccentThree = R.color.preview_mocha_dark_one,
    ),
    MOCHA_LIGHT(
        key = "mocha_light",
        styleRes = R.style.Theme_LearningMocha,
        nightMode = AppCompatDelegate.MODE_NIGHT_NO,
        labelRes = R.string.theme_mocha_light,
        captionRes = R.string.theme_mocha_light_caption,
        previewBackground = R.color.preview_mocha_light_bg,
        previewChrome = R.color.preview_mocha_light_chrome,
        previewAccentOne = R.color.preview_mocha_light_one,
        previewAccentTwo = R.color.preview_mocha_light_two,
        previewAccentThree = R.color.preview_mocha_light_three,
    ),
    MOCHA_DARK(
        key = "mocha_dark",
        styleRes = R.style.Theme_LearningMocha,
        nightMode = AppCompatDelegate.MODE_NIGHT_YES,
        labelRes = R.string.theme_mocha_dark,
        captionRes = R.string.theme_mocha_dark_caption,
        previewBackground = R.color.preview_mocha_dark_bg,
        previewChrome = R.color.preview_mocha_dark_chrome,
        previewAccentOne = R.color.preview_mocha_dark_one,
        previewAccentTwo = R.color.preview_mocha_dark_two,
        previewAccentThree = R.color.preview_mocha_dark_three,
    ),
    ROSE_PINE(
        key = "rose_pine",
        styleRes = R.style.Theme_LearningMocha_RosePine,
        nightMode = AppCompatDelegate.MODE_NIGHT_YES,
        labelRes = R.string.theme_rose_pine,
        captionRes = R.string.theme_rose_pine_caption,
        previewBackground = R.color.rp_cream,
        previewChrome = R.color.rp_header_bg,
        previewAccentOne = R.color.rp_brown,
        previewAccentTwo = R.color.rp_sage,
        previewAccentThree = R.color.rp_favorite,
    ),
    CATPPUCCIN(
        key = "catppuccin",
        styleRes = R.style.Theme_LearningMocha_Catppuccin,
        nightMode = AppCompatDelegate.MODE_NIGHT_YES,
        labelRes = R.string.theme_catppuccin,
        captionRes = R.string.theme_catppuccin_caption,
        previewBackground = R.color.ctp_cream,
        previewChrome = R.color.ctp_header_bg,
        previewAccentOne = R.color.ctp_brown,
        previewAccentTwo = R.color.ctp_sage,
        previewAccentThree = R.color.ctp_favorite,
    ),
    NORD(
        key = "nord",
        styleRes = R.style.Theme_LearningMocha_Nord,
        nightMode = AppCompatDelegate.MODE_NIGHT_YES,
        labelRes = R.string.theme_nord,
        captionRes = R.string.theme_nord_caption,
        previewBackground = R.color.nord_cream,
        previewChrome = R.color.nord_header_bg,
        previewAccentOne = R.color.nord_brown,
        previewAccentTwo = R.color.nord_sage,
        previewAccentThree = R.color.nord_favorite,
    ),
    ;

    /** Called before the first inflate — from Application.onCreate and on every theme change. */
    fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    /**
     * Switches the running app over. Changing the night mode already recreates the Activity, so
     * doing it again would only cost a second flash; a palette swap inside the same night mode
     * has to ask for the recreate itself, because nothing else noticed.
     */
    fun applyTo(activity: Activity) {
        val recreates = AppCompatDelegate.getDefaultNightMode() != nightMode
        applyNightMode()
        if (!recreates) activity.recreate()
    }

    companion object {
        val DEFAULT = MOCHA_SYSTEM

        /** Unknown keys — an older build, a hand-edited preference — fall back to the default. */
        fun of(key: String?): AppTheme = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
