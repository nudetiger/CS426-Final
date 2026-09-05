package com.cs426.learningmocha.ui.common

import android.view.View
import com.cs426.learningmocha.R

/** Alternating row wash, so a long list is easier to scan than a flat cream slab. */
fun View.stripe(position: Int) {
    setBackgroundColor(
        context.themeColor(if (position % 2 == 0) R.attr.mochaRowEven else R.attr.mochaRowOdd),
    )
}
