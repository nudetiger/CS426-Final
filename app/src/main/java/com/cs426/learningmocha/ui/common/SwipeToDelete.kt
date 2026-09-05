package com.cs426.learningmocha.ui.common

import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.cs426.learningmocha.R

/**
 * Swipe a row towards the start edge to delete it.
 *
 * Browse already had this gesture, but with nothing drawn behind the row: it slid away over the
 * list background and a dialog appeared out of nowhere. There was no way to discover the gesture
 * existed, and no way to tell mid-swipe what letting go would do. The panel and the bin are that
 * missing half — they grow under the finger, and the bin only fades in once the swipe has gone
 * far enough to count, so brushing a row sideways while scrolling still says nothing.
 *
 * Deleting is always confirmed by the caller. This only ever asks.
 */
object SwipeToDelete {

    /** Fraction of the row that has to be crossed before letting go asks to delete. */
    private const val THRESHOLD = 0.45f

    /** Where the bin starts fading in. Below this the panel alone answers "something is here". */
    private const val REVEAL_FROM = 0.2f

    /**
     * @param onConfirm called with the swiped position and a lambda that puts the row back.
     *   Call that lambda from the dialog's cancel and dismiss paths, or the row stays swiped
     *   off screen with its data still in the database.
     */
    fun attach(list: RecyclerView, onConfirm: (position: Int, restore: () -> Unit) -> Unit) {
        val context = list.context
        val paint = Paint().apply {
            isAntiAlias = true
            color = context.themeColor(R.attr.mochaError)
        }
        // Mutated so tinting this copy cannot recolour every other ic_delete in the app.
        val bin = ContextCompat.getDrawable(context, R.drawable.ic_delete)?.mutate()?.apply {
            setTint(context.themeColor(R.attr.mochaOnPrimary))
        }
        val binSize = context.resources.getDimensionPixelSize(R.dimen.icon_small)
        val gutter = context.resources.getDimensionPixelSize(R.dimen.space_m)

        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean = false

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = THRESHOLD

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                onConfirm(position) { list.adapter?.notifyItemChanged(position) }
            }

            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean,
            ) {
                val row = viewHolder.itemView
                // dX >= 0 covers the settle animation after a cancelled swipe, where drawing a
                // sliver of red on a row that is going nowhere reads as a glitch.
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0f && row.width > 0) {
                    val edge = row.right + dX
                    canvas.drawRect(
                        edge,
                        row.top.toFloat(),
                        row.right.toFloat(),
                        row.bottom.toFloat(),
                        paint,
                    )
                    if (bin != null) {
                        val left = row.right - gutter - binSize
                        // Held back until the panel is actually wide enough to hold the bin,
                        // so the glyph never floats over the row it is uncovering.
                        if (left >= edge) {
                            val travelled = (-dX / row.width).coerceIn(0f, 1f)
                            val fade = ((travelled - REVEAL_FROM) / (THRESHOLD - REVEAL_FROM))
                                .coerceIn(0f, 1f)
                            val top = row.top + (row.height - binSize) / 2
                            bin.alpha = (fade * 255).toInt()
                            bin.setBounds(left, top, left + binSize, top + binSize)
                            bin.draw(canvas)
                        }
                    }
                }
                super.onChildDraw(
                    canvas,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive,
                )
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(list)
    }
}
