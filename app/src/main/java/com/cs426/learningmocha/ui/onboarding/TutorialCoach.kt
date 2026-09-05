package com.cs426.learningmocha.ui.onboarding

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.ViewCoachMarkBinding

/**
 * The four-card walkthrough shown once, right after onboarding: a small card floated over each
 * bottom tab in turn, the way Notion, Todoist and Trello introduce their chrome. The starter
 * posts already teach the concepts, so this only names the buttons and gets out of the way —
 * Skip ends it at any point, and either ending marks it seen for good.
 */
class TutorialCoach private constructor(
    private val bottomNav: ViewGroup,
    private val onFinished: () -> Unit,
) {

    private var popup: PopupWindow? = null
    private var step = 0

    private fun show() {
        val context = bottomNav.context
        val mark = STEPS.getOrNull(step) ?: return finish()
        val anchor = bottomNav.findViewById<View>(mark.tabId) ?: return finish()
        val binding = ViewCoachMarkBinding.inflate(LayoutInflater.from(context))
        binding.coachTitle.setText(mark.title)
        binding.coachBody.setText(mark.body)
        binding.coachStep.text = context.getString(
            R.string.tutorial_step,
            step + 1,
            STEPS.size,
        )
        binding.coachNext.setText(
            if (step == STEPS.lastIndex) R.string.tutorial_done else R.string.tutorial_next,
        )
        binding.coachSkip.setOnClickListener { finish() }
        binding.coachNext.setOnClickListener {
            popup?.dismiss()
            popup = null
            step++
            if (step < STEPS.size) show() else finish()
        }

        val margin = dp(context, MARGIN_DP)
        val width = context.resources.displayMetrics.widthPixels - margin * 2
        val content = binding.root
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val window = PopupWindow(content, width, content.measuredHeight).apply {
            // Outside taps must not dismiss: the card carries the only two controls that
            // record the walkthrough as seen, and a stray tap would strand it half-shown.
            isOutsideTouchable = false
            isFocusable = false
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            elevation = dp(context, ELEVATION_DP).toFloat()
        }
        popup = window
        // Anchored to the tab, but laid out full width: xoff pulls the card back to the screen
        // margin so a card near the last tab is not clipped off the right edge.
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        window.showAsDropDown(
            anchor,
            margin - location[0],
            -(anchor.height + content.measuredHeight + dp(context, GAP_DP)),
        )
    }

    private fun finish() {
        popup?.dismiss()
        popup = null
        onFinished()
    }

    /** Called when the host activity goes away mid-walkthrough, so the window cannot leak. */
    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    /**
     * Ends the walkthrough because the user went somewhere instead of reading it. Counted as
     * seen, exactly like Skip: a card describing Browse must not hang around over Search, and
     * someone who is already exploring does not need to be introduced again next launch.
     */
    fun skip() {
        finish()
    }

    private data class Mark(
        @IdRes val tabId: Int,
        @StringRes val title: Int,
        @StringRes val body: Int,
    )

    companion object {
        private const val MARGIN_DP = 16
        private const val GAP_DP = 8
        private const val ELEVATION_DP = 8

        private val STEPS = listOf(
            Mark(R.id.browseFragment, R.string.tutorial_browse_title, R.string.tutorial_browse_body),
            Mark(R.id.searchFragment, R.string.tutorial_search_title, R.string.tutorial_search_body),
            Mark(R.id.chatFragment, R.string.tutorial_chat_title, R.string.tutorial_chat_body),
            Mark(
                R.id.settingsFragment,
                R.string.tutorial_settings_title,
                R.string.tutorial_settings_body,
            ),
        )

        /**
         * Starts the walkthrough once [bottomNav] has been laid out — the cards are positioned
         * against real tab coordinates, which do not exist yet on the frame that requests them.
         */
        fun start(bottomNav: ViewGroup, onFinished: () -> Unit): TutorialCoach {
            val coach = TutorialCoach(bottomNav, onFinished)
            bottomNav.post { coach.show() }
            return coach
        }

        private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
    }
}
