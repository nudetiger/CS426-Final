package com.cs426.learningmocha.ui.common

import android.content.res.ColorStateList
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.databinding.ViewStatusMeterBinding

/**
 * Draws [SubtreeStats] into `view_status_meter.xml`: a stacked bar sized by share, and a legend
 * that doubles as a filter control.
 *
 * The weights are the raw counts rather than percentages, so a folder of three posts still
 * divides its bar exactly in thirds instead of into rounded 33% stripes that leave a gap.
 */
object StatusMeterBinder {

    private val ORDER = listOf(
        LearningStatus.NONE,
        LearningStatus.READING,
        LearningStatus.IN_PROGRESS,
        LearningStatus.FINISHED,
    )

    /**
     * @param onPick invoked with the status whose legend column was tapped; null makes the
     *   legend inert, which is what a read-only summary wants
     * @param showSummary false drops the "N posts in here" line. Declared before [onPick] so
     *   a trailing lambda at a call site still binds to [onPick]. The prerequisite card in the
     *   reader carries its own caption ("1 of 2 started"), and the folder wording underneath it
     *   both repeats the count and calls a list of prerequisites a folder.
     */
    fun bind(
        binding: ViewStatusMeterBinding,
        stats: SubtreeStats,
        showSummary: Boolean = true,
        onPick: ((LearningStatus) -> Unit)? = null,
    ) {
        val context = binding.root.context
        val resources = context.resources

        binding.meterSummary.isVisible = showSummary
        binding.meterSummary.text = if (stats.isEmpty) {
            context.getString(R.string.browse_meter_empty)
        } else {
            resources.getQuantityString(R.plurals.browse_meter_summary, stats.total, stats.total)
        }
        // An empty folder shows the bare track: a meter of four zero-width stripes would read
        // as a rendering bug rather than as "nothing in here yet".
        binding.meterBar.isVisible = !stats.isEmpty

        val bars = listOf(
            binding.meterNone,
            binding.meterReading,
            binding.meterProgress,
            binding.meterFinished,
        )
        val columns = listOf(
            binding.legendNone,
            binding.legendReading,
            binding.legendProgress,
            binding.legendFinished,
        )
        val dots = listOf(
            binding.legendNoneDot,
            binding.legendReadingDot,
            binding.legendProgressDot,
            binding.legendFinishedDot,
        )
        val values = listOf(
            binding.legendNoneValue,
            binding.legendReadingValue,
            binding.legendProgressValue,
            binding.legendFinishedValue,
        )

        binding.meterBar.weightSum = stats.total.toFloat().coerceAtLeast(1f)
        ORDER.forEachIndexed { index, status ->
            val count = stats.count(status)
            val ink = context.themeColor(NodePalette.statusInk(status))

            bars[index].layoutParams =
                (bars[index].layoutParams as LinearLayout.LayoutParams).apply {
                    width = 0
                    weight = count.toFloat()
                }
            bars[index].setBackgroundColor(ink)
            // A zero-weight child still measures its minimum, so it has to be gone outright.
            bars[index].visibility = if (count == 0) View.GONE else View.VISIBLE

            dots[index].imageTintList = ColorStateList.valueOf(ink)
            values[index].text =
                context.getString(R.string.browse_meter_percent, stats.percent(status))
            columns[index].contentDescription = context.getString(
                R.string.browse_meter_legend_cd,
                context.getString(status.labelRes()),
                count,
                stats.total,
            )
            if (onPick == null) {
                columns[index].isClickable = false
                columns[index].setOnClickListener(null)
            } else {
                columns[index].isClickable = true
                columns[index].setOnClickListener { onPick(status) }
            }
        }
        binding.meterBar.requestLayout()
    }
}
