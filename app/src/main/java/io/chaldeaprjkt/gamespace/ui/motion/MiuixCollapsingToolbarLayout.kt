package io.chaldeaprjkt.gamespace.ui.motion

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import io.chaldeaprjkt.gamespace.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * CollapsingToolbarLayout voi tieu de nho ap dung Miuix Folme Spring.
 * Giu nguyen tieu de lon goc va ho tro tieu de nho can le start tranh nut Back.
 */
class MiuixCollapsingToolbarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : CollapsingToolbarLayout(context, attrs) {
    private val smallTitleStyle = AppCompatTextView(context).apply {
        setTextAppearance(R.style.TextAppearance_GameSpace_CollapsingToolbar_Collapsed)
    }
    private val titlePaint = TextPaint(smallTitleStyle.paint)
    private val toolbarBounds = Rect()
    private val navigationBounds = Rect()
    private var appBar: AppBarLayout? = null

    // Trang thai Folme Spring cho tieu de nho (alpha va translationY)
    private var paintAlpha = 0f
    private var paintAlphaVelocity = 0f
    private var paintTranslationY = MiuixTitleMotion.TransitionDistancePx
    private var paintTranslationYVelocity = 0f

    private var targetAlpha = 0f
    private var targetTranslationY = MiuixTitleMotion.TransitionDistancePx
    private var isSmallTitleVisible = false

    private var initialOffsetApplied = false
    private var framePosted = false
    private var lastFrameNanos = 0L
    private var durationScale = 1f

    private val paintFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePosted = false
            if (!isAttachedToWindow) return
            if (!ValueAnimator.areAnimatorsEnabled()) {
                paintAlpha = targetAlpha
                paintAlphaVelocity = 0f
                paintTranslationY = targetTranslationY
                paintTranslationYVelocity = 0f
                invalidate()
                return
            }
            val dt = if (lastFrameNanos == 0L) 0f else
                ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f / durationScale)
                    .coerceIn(0f, 0.064f)
            lastFrameNanos = frameTimeNanos

            val visible = isSmallTitleVisible
            val sampleAlpha = MiuixTitleMotion.alphaStep(
                paintAlpha, paintAlphaVelocity, visible, dt,
            )
            val sampleY = MiuixTitleMotion.translationYStep(
                paintTranslationY, paintTranslationYVelocity, visible, dt,
            )
            paintAlpha = sampleAlpha.value
            paintAlphaVelocity = sampleAlpha.velocity
            paintTranslationY = sampleY.value
            paintTranslationYVelocity = sampleY.velocity
            invalidate()
            if (!sampleAlpha.settled || !sampleY.settled) {
                postPaintFrame()
            } else {
                lastFrameNanos = 0L
            }
        }
    }

    private fun postPaintFrame() {
        if (framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(paintFrame)
    }

    private val offsetListener = AppBarLayout.OnOffsetChangedListener { bar, offset ->
        val range = bar.totalScrollRange.toFloat()
        val fraction = MiuixTitleMotion.collapseFraction(offset.toFloat(), range)
        val visible = MiuixTitleMotion.isSmallTitleVisible(fraction)
        val stateChanged = (visible != isSmallTitleVisible)
        isSmallTitleVisible = visible

        targetAlpha = if (visible) 1f else 0f
        targetTranslationY = if (visible) 0f else MiuixTitleMotion.TransitionDistancePx
        durationScale = ValueAnimator.getDurationScale().coerceAtLeast(0.01f)

        if (!initialOffsetApplied || !ValueAnimator.areAnimatorsEnabled()) {
            // Khoi tao hoac khoi phuc trang thai ma khong chay lai hoat hoa
            paintAlpha = targetAlpha
            paintAlphaVelocity = 0f
            paintTranslationY = targetTranslationY
            paintTranslationYVelocity = 0f
            initialOffsetApplied = bar.totalScrollRange > 0
        } else if (stateChanged || paintAlpha != targetAlpha || paintTranslationY != targetTranslationY ||
            paintAlphaVelocity != 0f || paintTranslationYVelocity != 0f
        ) {
            // Chuyen huong lo xo ma khong reset thoi gian va giu van toc dong hoc
            postPaintFrame()
        }
        invalidate()
    }

    init {
        // Chi ve lai ban collapsed, giu nguyen tieu de lon he thong va accessibility
        setCollapsedTitleTextColor(Color.TRANSPARENT)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        appBar = parent as? AppBarLayout
        appBar?.let { bar ->
            bar.addOnOffsetChangedListener(offsetListener)
            val behavior = (bar.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior
                as? AppBarLayout.Behavior
            offsetListener.onOffsetChanged(bar, behavior?.topAndBottomOffset ?: 0)
        }
    }

    override fun onDetachedFromWindow() {
        appBar?.removeOnOffsetChangedListener(offsetListener)
        Choreographer.getInstance().removeFrameCallback(paintFrame)
        framePosted = false
        lastFrameNanos = 0L
        initialOffsetApplied = false
        appBar = null
        super.onDetachedFromWindow()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val alpha = paintAlpha.coerceIn(0f, 1f)
        if (alpha <= 0f) return
        val toolbar = findViewById<Toolbar>(R.id.toolbar) ?: return
        val text = title ?: return
        toolbarBounds.set(0, 0, toolbar.width, toolbar.height)
        offsetDescendantRectToMyCoords(toolbar, toolbarBounds)
        // Chuan AOSP: khoang cach chu ← canh quang hoc vong back 40dp = 24dp
        // (khong dung canh ImageButton touch-target — de bi lech).
        var hasNavigation = false
        var navigationCenterX = toolbarBounds.exactCenterX()
        var navigationCenterY = toolbarBounds.exactCenterY()
        for (index in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(index)
            if (child is ImageButton && child.visibility == View.VISIBLE &&
                (child.drawable === toolbar.navigationIcon || toolbar.navigationIcon != null)
            ) {
                navigationBounds.set(0, 0, child.width, child.height)
                offsetDescendantRectToMyCoords(child, navigationBounds)
                hasNavigation = true
                navigationCenterX = navigationBounds.exactCenterX()
                navigationCenterY = navigationBounds.exactCenterY()
                break
            }
        }
        val gapPx = resources.getDimension(R.dimen.gamespace_collapsed_title_nav_gap)
        val edgePx = resources.getDimension(R.dimen.gamespace_toolbar_edge_inset)
        val backSizePx = resources.getDimension(R.dimen.gamespace_expressive_back_icon_size)
        val titleStartX = MiuixTitleMotion.collapsedTitleStartX(
            toolbarLeftPx = toolbarBounds.left.toFloat(),
            navCenterXPx = navigationCenterX,
            hasNavigation = hasNavigation,
            backIconSizePx = backSizePx,
            gapPx = gapPx,
            contentInsetStartPx = max(edgePx, toolbar.currentContentInsetStart.toFloat()),
            titleMarginStartPx = toolbar.titleMarginStart.toFloat(),
        )
        val end = max(edgePx, toolbar.currentContentInsetEnd.toFloat()) +
            toolbar.titleMarginEnd
        val startInsetFromLeft = titleStartX - toolbarBounds.left
        val availableWidth = (width - startInsetFromLeft - end).coerceAtLeast(0f)
        titlePaint.set(smallTitleStyle.paint)
        titlePaint.color = smallTitleStyle.textColors.getColorForState(
            drawableState, smallTitleStyle.currentTextColor,
        )
        titlePaint.alpha = (Color.alpha(titlePaint.color) * alpha).roundToInt()
        val label = TextUtils.ellipsize(text, titlePaint, availableWidth, TextUtils.TruncateAt.END)
        val labelWidth = titlePaint.measureText(label, 0, label.length)
        val baseline = navigationCenterY -
            (titlePaint.ascent() + titlePaint.descent()) / 2f +
            paintTranslationY
        val x = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            val mirrorStart = width - titleStartX
            mirrorStart - labelWidth
        } else {
            titleStartX
        }
        canvas.drawText(label, 0, label.length, x, baseline, titlePaint)
    }
}
