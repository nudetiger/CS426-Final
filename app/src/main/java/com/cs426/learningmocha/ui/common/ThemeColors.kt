package com.cs426.learningmocha.ui.common

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/**
 * Reads one palette token off the current theme.
 *
 * The palette moved out of `values/colors.xml` and into theme attributes (see
 * `values/attrs_theme.xml`) so that Rose Pine, Catppuccin and Nord can replace the whole set
 * at once. Layouts and drawables get that for free through `?attr/`; code has to ask, and this
 * is the one place that knows how. Callers pass an `R.attr.*` id where they used to pass
 * `R.color.*`, so the shape of every call site is unchanged.
 */
@ColorInt
fun Context.themeColor(@AttrRes attr: Int): Int {
    val value = TypedValue()
    // resolveRefs = true: the attributes point at colour resources, and the caller wants the
    // colour, not the reference.
    if (!theme.resolveAttribute(attr, value, true)) return 0
    return if (value.resourceId != 0) {
        androidx.core.content.ContextCompat.getColor(this, value.resourceId)
    } else {
        value.data
    }
}
