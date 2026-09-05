package com.cs426.learningmocha.ui.graph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.repo.GraphEdge
import com.cs426.learningmocha.data.repo.GraphEdgeType
import com.cs426.learningmocha.data.repo.GraphNode
import com.cs426.learningmocha.ui.common.themeColor
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Canvas renderer for the knowledge graph: pan, pinch-zoom, double-tap to fit, tap to select.
 *
 * Positions arrive already laid out in a square "world" of [worldSize] units (see
 * `ForceLayout`), so this view only ever does an affine world → screen transform. No layout
 * math happens here, which keeps drawing cheap even for the 250-node cap.
 */
class GraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onNodeTap: ((Long) -> Unit)? = null

    /** Fired when an already-selected node is tapped again. */
    var onNodeOpen: ((Long) -> Unit)? = null

    var onBackgroundTap: (() -> Unit)? = null

    private var nodes: List<GraphNode> = emptyList()
    private var edges: List<GraphEdge> = emptyList()
    private var labels: List<CharSequence> = emptyList()
    private var positions: FloatArray = FloatArray(0)
    private var worldSize = 1f
    private var focusIndex = -1
    private var selectedId: Long? = null

    private var scale = 1f
    private var translateX = 0f
    private var translateY = 0f
    private var fitScale = 1f
    private var minScale = 0.25f
    private var maxScale = 8f
    private var viewportReady = false
    private var pendingRestore = false

    private val density = resources.displayMetrics.density
    private val viewportPadding = dp(20f)
    private val baseRadius = dp(5f)
    private val degreeRadius = dp(2.4f)
    private val maxRadius = dp(15f)
    private val touchSlop = dp(14f)
    private val labelMaxWidth = dp(96f)
    private val labelSpacingMin = dp(56f)
    private val labelGap = dp(4f)

    private val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.6f)
        strokeCap = Paint.Cap.ROUND
        color = color(R.attr.mochaBrown)
        alpha = 150
    }

    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.8f)
        color = color(R.attr.mochaTaupe)
        alpha = 90
    }

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nodeOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = color(R.attr.mochaCream)
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = color(R.attr.mochaOnPrimary)
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = color(R.attr.mochaTextPrimary)
    }

    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.attr.mochaTextSecondary)
        textSize = resources.getDimension(R.dimen.text_caption)
        textAlign = Paint.Align.CENTER
    }

    private val selectedLabelPaint = TextPaint(labelPaint).apply {
        color = color(R.attr.mochaTextPrimary)
        isFakeBoldText = true
    }

    private val starPath = Path()

    /** Label boxes already drawn this frame, for the collision check in [drawLabels]. */
    private val placed = ArrayList<RectF>()
    private var linkPoints = FloatArray(0)
    private var tagPoints = FloatArray(0)
    private var linkPointCount = 0
    private var tagPointCount = 0

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val target = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
                val factor = if (scale == 0f) 1f else target / scale
                if (factor == 1f) return true
                translateX = detector.focusX - (detector.focusX - translateX) * factor
                translateY = detector.focusY - (detector.focusY - translateY) * factor
                scale = target
                invalidate()
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (scaleDetector.isInProgress) return true
                translateX -= distanceX
                translateY -= distanceY
                invalidate()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                handleTap(e.x, e.y)
                // Routes the tap through View's click machinery so TalkBack and any
                // registered OnClickListener see it, not just the gesture detector.
                performClick()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                computeFit()
                resetTransform()
                invalidate()
                return true
            }
        },
    )

    /**
     * @param positions flattened world coordinates from `ForceLayout`; passing the same array
     *   instance again (a re-collect after rotation) keeps the current pan/zoom
     */
    fun setGraph(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        positions: FloatArray,
        worldSize: Float,
        focusIndex: Int,
    ) {
        val sameData = positions === this.positions
        this.nodes = nodes
        this.edges = edges
        this.positions = positions
        this.worldSize = if (worldSize > 0f) worldSize else 1f
        this.focusIndex = focusIndex
        labels = nodes.map { node ->
            TextUtils.ellipsize(node.title, labelPaint, labelMaxWidth, TextUtils.TruncateAt.END)
        }
        if (!sameData) viewportReady = false
        ensureViewport()
        invalidate()
    }

    fun setSelectedId(id: Long?) {
        if (selectedId == id) return
        selectedId = id
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ensureViewport()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (nodes.isEmpty() || positions.size < nodes.size * 2) return

        buildEdgePoints()
        if (tagPointCount > 0) canvas.drawLines(tagPoints, 0, tagPointCount, tagPaint)
        if (linkPointCount > 0) canvas.drawLines(linkPoints, 0, linkPointCount, linkPaint)

        for (index in nodes.indices) {
            val node = nodes[index]
            val cx = screenX(index)
            val cy = screenY(index)
            val radius = radiusFor(node.degree)
            nodePaint.color = fillColor(node.status)
            canvas.drawCircle(cx, cy, radius, nodePaint)
            canvas.drawCircle(cx, cy, radius, nodeOutlinePaint)
            if (node.favorite) drawFavoriteAccent(canvas, cx, cy, radius)
            if (node.id == selectedId) {
                canvas.drawCircle(cx, cy, radius + dp(4f), selectionPaint)
            }
        }
        drawLabels(canvas)
    }

    /**
     * Labels are drawn after every dot, best-connected first, and one that would land on a label
     * already placed is dropped. Average spacing decides whether labels appear at all, so without
     * this two nodes that happen to sit close together overlap even in a sparse graph. The
     * selected node always keeps its label — it is the one the user asked about.
     */
    private fun drawLabels(canvas: Canvas) {
        val showLabels = labelsVisible()
        if (!showLabels && selectedId == null) return
        placed.clear()
        val order = nodes.indices.sortedWith(
            compareByDescending<Int> { nodes[it].id == selectedId }
                .thenByDescending { nodes[it].degree },
        )
        for (index in order) {
            val node = nodes[index]
            val selected = node.id == selectedId
            if (!showLabels && !selected) continue
            val label = labels.getOrNull(index) ?: node.title
            if (label.isEmpty()) continue
            val paint = if (selected) selectedLabelPaint else labelPaint
            val textWidth = paint.measureText(label, 0, label.length)
            val centre = labelCentre(cx = screenX(index), textWidth = textWidth)
            val top = screenY(index) + radiusFor(node.degree) + labelGap
            val box = RectF(
                centre - textWidth / 2f,
                top,
                centre + textWidth / 2f,
                top + paint.descent() - paint.ascent(),
            )
            if (!selected && placed.any { RectF.intersects(it, box) }) continue
            placed.add(box)
            canvas.drawText(label, 0, label.length, centre, top - paint.ascent(), paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    /**
     * Taps are recognised by [gestureDetector], not by [onTouchEvent] returning through
     * super, so the click callback has to be raised explicitly for accessibility.
     */
    override fun performClick(): Boolean = super.performClick()

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(KEY_SUPER, super.onSaveInstanceState())
        bundle.putFloat(KEY_SCALE, scale)
        bundle.putFloat(KEY_TRANSLATE_X, translateX)
        bundle.putFloat(KEY_TRANSLATE_Y, translateY)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        val restorable = state.containsKey(KEY_SCALE) && state.getFloat(KEY_SCALE) > 0f
        if (restorable) {
            scale = state.getFloat(KEY_SCALE)
            translateX = state.getFloat(KEY_TRANSLATE_X)
            translateY = state.getFloat(KEY_TRANSLATE_Y)
            pendingRestore = true
        }
        @Suppress("DEPRECATION")
        super.onRestoreInstanceState(state.getParcelable<Parcelable>(KEY_SUPER))
        ensureViewport()
    }

    private fun ensureViewport() {
        if (width == 0 || height == 0 || nodes.isEmpty()) return
        if (positions.size < nodes.size * 2) return
        computeFit()
        if (pendingRestore) {
            pendingRestore = false
            viewportReady = true
            scale = scale.coerceIn(minScale, maxScale)
            return
        }
        if (viewportReady) return
        viewportReady = true
        if (focusIndex in nodes.indices) {
            centreOnNode(focusIndex)
        } else {
            resetTransform()
        }
    }

    private fun computeFit() {
        if (width == 0 || height == 0) return
        val usableWidth = (width - 2f * viewportPadding).coerceAtLeast(1f)
        val usableHeight = (height - 2f * viewportPadding).coerceAtLeast(1f)
        fitScale = min(usableWidth / worldSize, usableHeight / worldSize).coerceAtLeast(0.01f)
        minScale = fitScale * 0.5f
        maxScale = fitScale * 8f
    }

    private fun resetTransform() {
        scale = fitScale
        translateX = (width - worldSize * scale) / 2f
        translateY = (height - worldSize * scale) / 2f
    }

    private fun centreOnNode(index: Int) {
        scale = (fitScale * 2.2f).coerceIn(minScale, maxScale)
        translateX = width / 2f - positions[index * 2] * scale
        translateY = height / 2f - positions[index * 2 + 1] * scale
    }

    private fun buildEdgePoints() {
        var links = 0
        var tags = 0
        for (edge in edges) {
            if (edge.type == GraphEdgeType.LINK) links++ else tags++
        }
        if (linkPoints.size < links * 4) linkPoints = FloatArray(links * 4)
        if (tagPoints.size < tags * 4) tagPoints = FloatArray(tags * 4)
        var linkIndex = 0
        var tagIndex = 0
        for (edge in edges) {
            if (edge.from !in nodes.indices || edge.to !in nodes.indices) continue
            val x1 = screenX(edge.from)
            val y1 = screenY(edge.from)
            val x2 = screenX(edge.to)
            val y2 = screenY(edge.to)
            if (edge.type == GraphEdgeType.LINK) {
                linkPoints[linkIndex++] = x1
                linkPoints[linkIndex++] = y1
                linkPoints[linkIndex++] = x2
                linkPoints[linkIndex++] = y2
            } else {
                tagPoints[tagIndex++] = x1
                tagPoints[tagIndex++] = y1
                tagPoints[tagIndex++] = x2
                tagPoints[tagIndex++] = y2
            }
        }
        linkPointCount = linkIndex
        tagPointCount = tagIndex
    }

    /** A cream star inside the circle reads as "starred" on every status colour. */
    private fun drawFavoriteAccent(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        if (radius < dp(7f)) {
            canvas.drawCircle(cx, cy, dp(1.8f), accentPaint)
            return
        }
        val outer = radius * 0.62f
        val inner = outer * 0.45f
        starPath.reset()
        for (point in 0 until 10) {
            val r = if (point % 2 == 0) outer else inner
            val angle = (-Math.PI / 2 + point * Math.PI / 5).toFloat()
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (point == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
        canvas.drawPath(starPath, accentPaint)
    }

    /** Labels only once neighbours are far enough apart that the text will not collide. */
    /**
     * Labels are centre-aligned, so a node near an edge would have half its title drawn
     * outside the view. Nudge the text box back inside instead of clipping it; the label
     * then sits slightly off-centre under the dot, which reads better than half a word.
     */
    private fun labelCentre(cx: Float, textWidth: Float): Float {
        val half = textWidth / 2f
        // Inset by the same padding the fit uses, not by the label's own gap: a title pushed
        // flush against the window edge reads as clipped even though every glyph is drawn.
        val min = viewportPadding + half
        val max = width - viewportPadding - half
        return if (min > max) cx else cx.coerceIn(min, max)
    }

    private fun labelsVisible(): Boolean {
        if (nodes.isEmpty()) return false
        val spacing = worldSize * scale / sqrt(nodes.size.toFloat())
        return spacing >= labelSpacingMin
    }

    private fun handleTap(x: Float, y: Float) {
        val index = nodeAt(x, y)
        if (index == null) {
            onBackgroundTap?.invoke()
            return
        }
        val id = nodes[index].id
        if (id == selectedId) onNodeOpen?.invoke(id) else onNodeTap?.invoke(id)
    }

    private fun nodeAt(x: Float, y: Float): Int? {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (index in nodes.indices) {
            val dx = screenX(index) - x
            val dy = screenY(index) - y
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= radiusFor(nodes[index].degree) + touchSlop &&
                distance < bestDistance
            ) {
                best = index
                bestDistance = distance
            }
        }
        return if (best >= 0) best else null
    }

    private fun radiusFor(degree: Int): Float =
        (baseRadius + degreeRadius * sqrt(degree.toFloat())).coerceAtMost(maxRadius)

    private fun fillColor(status: LearningStatus): Int = when (status) {
        LearningStatus.FINISHED -> color(R.attr.mochaSage)
        LearningStatus.READING -> color(R.attr.mochaBrown)
        LearningStatus.IN_PROGRESS -> color(R.attr.mochaBrownDeep)
        LearningStatus.NONE -> color(R.attr.mochaTaupe)
    }

    private fun screenX(index: Int): Float = positions[index * 2] * scale + translateX

    private fun screenY(index: Int): Float = positions[index * 2 + 1] * scale + translateY

    @ColorInt
    private fun color(@AttrRes id: Int): Int = context.themeColor(id)

    private fun dp(value: Float): Float = value * density

    private companion object {
        const val KEY_SUPER = "super"
        const val KEY_SCALE = "scale"
        const val KEY_TRANSLATE_X = "tx"
        const val KEY_TRANSLATE_Y = "ty"
    }
}
