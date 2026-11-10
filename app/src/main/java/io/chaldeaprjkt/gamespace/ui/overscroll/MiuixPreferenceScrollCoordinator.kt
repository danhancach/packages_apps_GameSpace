/*
 * Copyright (C) 2026 danhancach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.ui.overscroll

import android.content.Context
import android.graphics.Canvas
import android.provider.Settings
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import io.chaldeaprjkt.gamespace.R
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.roundToInt

/**
 * Attach Preference list: EdgeEffect distance + settle; bind [MiuixAppBarScrollBehavior].
 *
 * Call-chain (live):
 *   ACTION_DOWN → reacquire (stop RV/AppBar fling + edge settle B1)
 *   RV.releaseVerticalGlow → EdgeEffect.getDistance/onPullDistance (reverse truoc nested)
 *   AppBar Behavior.onNestedPreScroll → MiuixScrollPhase.preScroll → header super
 *   RV content scroll
 *   Behavior.onNestedScroll → MiuixScrollPhase.postScroll → expand + edge
 *   RecyclerView OnFlingListener → F0 + child remainder; EdgeEffect absorb → I0
 *
 * Stretch subordinate — translationY painted; khong nuot gesture X (touch return false).
 */
object MiuixPreferenceScrollCoordinator {
    private val attached = WeakHashMap<RecyclerView, Attachment>()

    private class Attachment(
        val controller: PhaseStretchController,
        val attachListener: View.OnAttachStateChangeListener,
        val touchListener: RecyclerView.OnItemTouchListener,
        val previousFlingListener: RecyclerView.OnFlingListener?,
    )

    fun attach(list: RecyclerView) {
        if (attached.containsKey(list)) return
        val controller = PhaseStretchController(list)
        list.overScrollMode = View.OVER_SCROLL_ALWAYS
        list.isNestedScrollingEnabled = true
        list.clipChildren = true
        list.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                val top = direction == DIRECTION_TOP
                return PhaseGatedEdgeEffect(view.context, controller, top)
            }
        }
        // Item touch observation also sees DOWN on clickable preference children.
        // A View.OnTouchListener on the list itself misses those events.
        val touchListener = object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> controller.reacquireFromTouch()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> controller.release()
                }
                return false
            }
        }
        list.addOnItemTouchListener(touchListener)
        val previousFlingListener = list.onFlingListener
        list.onFlingListener = object : RecyclerView.OnFlingListener() {
            override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                if (controller.forwardingFling) return false
                if (previousFlingListener?.onFling(velocityX, velocityY) == true) return true
                return controller.handoffFling(velocityX, velocityY)
            }
        }
        controller.bindAppBarBehavior()
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                controller.bindAppBarBehavior()
            }
            override fun onViewDetachedFromWindow(v: View) {
                detach(list)
            }
        }
        attached[list] = Attachment(controller, attachListener, touchListener, previousFlingListener)
        list.addOnAttachStateChangeListener(attachListener)
    }

    fun detach(list: RecyclerView) {
        val att = attached.remove(list) ?: return
        list.removeOnAttachStateChangeListener(att.attachListener)
        list.removeOnItemTouchListener(att.touchListener)
        list.onFlingListener = att.previousFlingListener
        att.controller.dispose()
        list.translationY = 0f
        list.edgeEffectFactory = RecyclerView.EdgeEffectFactory()
    }

    /**
     * Triet tieu toan bo do dan overscroll truc doc khi cu chi ngang chiem uu the (doi tab).
     * Miuix §4A.3 / SameGesturePagerScrollOwner.
     */
    fun confirmHorizontalTakeoverClearTension() {
        for (att in attached.values) {
            att.controller.confirmHorizontalTakeoverClearTension()
        }
    }
}

/**
 * Stretch state + Choreographer settle (hoi tu ve 0, mot scale duy nhat).
 */
internal class PhaseStretchController(
    private val host: RecyclerView,
) : PhaseScrollOwner {
    private var state = StretchMath.TensionState()
    private var generation = 0L
    private var settleRunning = false
    private var settlePos = 0.0
    private var settleVel = 0.0
    private var settlePrev = 0.0
    private var settlePeriod = StretchMath.SettlePeriodNormal
    private var lastFrameNanos = -1L
    private var boundBehavior: MiuixAppBarScrollBehavior? = null
    var forwardingFling = false
        private set

    fun isSettling(): Boolean = settleRunning

    /**
     * Triet tieu do dan overscroll khi cu chi ngang tiep quan (Miuix §4A.3).
     * Ngat ngay lap tuc cuon doc (fling) cua RecyclerView con (LSPosed hc2).
     */
    fun confirmHorizontalTakeoverClearTension() {
        generation += 1
        stopSettleFrames()
        state = StretchMath.TensionState()
        host.stopScroll()
        publish()
    }

    /** F0 owns its fraction; only the remaining velocity starts the child fling. */
    fun handoffFling(velocityX: Int, velocityY: Int): Boolean {
        val velocityYInMiuix = -velocityY.toFloat()
        val pre = MiuixScrollPhase.flingPreConsumedY(state.visualE, velocityYInMiuix)
        if (pre == 0f) return false
        startSettle(velocityYInMiuix)
        val toChildY = -(velocityYInMiuix - pre).roundToInt()
        var childStarted = false
        if (velocityX != 0 || toChildY != 0) {
            forwardingFling = true
            try {
                childStarted = host.fling(velocityX, toChildY)
            } finally {
                forwardingFling = false
            }
        }
        // The outer fling is handled even when F0 owns all Y velocity. In that case
        // no ViewFlinger will later reset RecyclerView's DRAGGING state for us.
        if (!childStarted) host.stopScroll()
        return true
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!settleRunning) return
            val gen = generation
            val scale = animatorScale()
            if (scale <= 0f) {
                finishSettleZero(gen)
                return
            }
            if (lastFrameNanos < 0L) {
                lastFrameNanos = frameTimeNanos
                if (gen == generation && settleRunning) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
                return
            }
            val dtSec = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0)
                .coerceIn(0.001, 0.016)
            lastFrameNanos = frameTimeNanos
            // Mot scale duy nhat: keo dai/nhanh wall-clock; KHONG nhan them period VA.
            val scaledDt = (dtSec / scale.toDouble().coerceAtLeast(0.01)).coerceIn(0.001, 0.016)
            val step = StretchMath.settleStep(settlePos, settleVel, scaledDt, settlePeriod)
            if (StretchMath.shouldClearSettle(step.first, step.second, settlePrev)) {
                finishSettleZero(gen)
                return
            }
            settlePrev = settlePos
            settlePos = step.first
            settleVel = step.second
            if (gen != generation) return
            state = StretchMath.TensionState(rawF = 0f, visualE = settlePos.toFloat())
            publish()
            if (abs(settlePos) < StretchMath.VisualEpsilonPx &&
                abs(settleVel) < StretchMath.VisualEpsilonPx
            ) {
                finishSettleZero(gen)
                return
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun boundG(): Float = host.height.toFloat().coerceAtLeast(0f)

    fun hasTension(): Boolean =
        abs(state.visualE) > StretchMath.VisualEpsilonPx || settleRunning

    fun peekVisualE(): Float = state.visualE

    fun peekRawF(): Float = state.rawF

    fun findAppBar(): AppBarLayout? {
        var p: Any? = host.parent
        while (p != null) {
            if (p is ViewGroup) {
                val found = p.findViewById<AppBarLayout>(R.id.app_bar)
                if (found != null) return found
            }
            p = (p as? View)?.parent
        }
        return null
    }

    fun bindAppBarBehavior() {
        val appBar = findAppBar() ?: return
        val lp = appBar.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        val behavior = when (val existing = lp.behavior) {
            is MiuixAppBarScrollBehavior -> existing
            else -> MiuixAppBarScrollBehavior().also { lp.behavior = it }
        }
        behavior.attachPhaseOwner(this)
        boundBehavior = behavior
    }

    override fun liveCaps(): MiuixScrollPhase.Caps {
        val appBar = findAppBar()
        return MiuixScrollPhase.capsFromViewMetrics(
            appBarTop = appBar?.top ?: 0,
            totalScrollRange = appBar?.totalScrollRange ?: 0,
            contentUpRemaining = contentScrollUpRemaining(),
            contentDownRemaining = contentScrollDownRemaining(),
            tensionE = state.visualE,
            boundG = boundG(),
        )
    }

    override fun commitTension(rawF: Float, visualE: Float) {
        cancelSettleKeepTension(bumpGeneration = true)
        state = StretchMath.TensionState(rawF = rawF, visualE = visualE)
        publish()
    }

    /** Finger down: dung header/list fling + edge settle; B1 reacquire. */
    fun reacquireFromTouch() {
        generation += 1
        stopSettleFrames()
        host.stopScroll()
        ViewCompat.stopNestedScroll(host, ViewCompat.TYPE_TOUCH)
        ViewCompat.stopNestedScroll(host, ViewCompat.TYPE_NON_TOUCH)
        // AppBar fling theo nested child — stopScroll/stopNestedScroll ngat chuoi.
        val g = boundG()
        if (state.rawF == 0f && abs(state.visualE) > StretchMath.VisualEpsilonPx && g > 0f) {
            state = StretchMath.TensionState(
                rawF = StretchMath.inverseStretch(state.visualE, g),
                visualE = state.visualE,
            )
        }
        settleRunning = false
    }

    fun contentScrollUpRemaining(): Float =
        if (host.canScrollVertically(-1)) {
            host.computeVerticalScrollOffset().toFloat().coerceAtLeast(2f)
        } else 0f

    fun contentScrollDownRemaining(): Float {
        if (!host.canScrollVertically(1)) return 0f
        val range = host.computeVerticalScrollRange() - host.computeVerticalScrollExtent()
        return (range - host.computeVerticalScrollOffset()).toFloat().coerceAtLeast(2f)
    }

    fun allowTopStretch(): Boolean {
        val caps = liveCaps()
        return caps.canTopStretch || (!caps.canContentScroll && caps.headerFullyExpanded)
    }

    fun allowBottomStretch(): Boolean {
        val caps = liveCaps()
        return caps.canBottomStretch || !caps.canContentScroll
    }

    private fun animatorScale(): Float =
        Settings.Global.getFloat(
            host.context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ).coerceAtLeast(0f)

    /**
     * Edge pull: tra raw px that su nap/giai (co the < |delta| khi clamp/reverse).
     */
    fun consumePullPx(deltaPx: Float, isTop: Boolean): Float {
        if (deltaPx == 0f) return 0f
        cancelSettleKeepTension(bumpGeneration = true)
        val g = boundG()
        if (g <= StretchMath.VisualEpsilonPx) return 0f

        val tensionSign = when {
            abs(state.rawF) > StretchMath.VisualEpsilonPx -> sign(state.rawF)
            abs(state.visualE) > StretchMath.VisualEpsilonPx -> sign(state.visualE)
            else -> 0f
        }
        if (tensionSign != 0f && sign(deltaPx) != tensionSign) {
            val (next, consumed) = StretchMath.reverseRelease(state, deltaPx, g)
            state = next
            publish()
            return consumed
        }
        val before = state.rawF
        val next = StretchMath.applyDelta(state, deltaPx, g)
        val actual = next.rawF - before
        state = next
        publish()
        return actual
    }

    fun absorb(velocity: Int, isTop: Boolean) {
        if (isTop && !allowTopStretch() && abs(state.visualE) <= StretchMath.VisualEpsilonPx) {
            return
        }
        if (!isTop && !allowBottomStretch() && abs(state.visualE) <= StretchMath.VisualEpsilonPx) {
            return
        }
        val signed = if (isTop) velocity.toFloat() else -velocity.toFloat()
        val settleVy = MiuixScrollPhase.flingSettleVelocity(state.visualE, signed)
            ?: signed
        startSettle(settleVy)
    }

    fun release() {
        if (!settleRunning) startSettle(0f)
    }

    private fun publish() {
        host.translationY = state.visualE
        host.invalidate()
    }

    private fun cancelSettleKeepTension(bumpGeneration: Boolean) {
        if (bumpGeneration) generation += 1
        stopSettleFrames()
        val g = boundG()
        if (state.rawF == 0f && abs(state.visualE) > StretchMath.VisualEpsilonPx && g > 0f) {
            state = StretchMath.TensionState(
                rawF = StretchMath.inverseStretch(state.visualE, g),
                visualE = state.visualE,
            )
        }
    }

    private fun stopSettleFrames() {
        if (settleRunning) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
        settleRunning = false
        lastFrameNanos = -1L
    }

    private fun finishSettleZero(gen: Long) {
        if (gen != generation) return
        stopSettleFrames()
        state = StretchMath.TensionState()
        publish()
    }

    private fun startSettle(initialVelocity: Float) {
        generation += 1
        val gen = generation
        stopSettleFrames()

        val scale = animatorScale()
        if (scale == 0f) {
            state = StretchMath.TensionState()
            publish()
            return
        }
        if (abs(state.visualE) <= StretchMath.VisualEpsilonPx &&
            abs(initialVelocity) <= StretchMath.VisualEpsilonPx
        ) {
            state = StretchMath.TensionState()
            publish()
            return
        }

        settlePeriod = StretchMath.settlePeriodSeconds(initialVelocity)
        settlePos = state.visualE.toDouble()
        settleVel = initialVelocity.toDouble()
        settlePrev = settlePos
        state = StretchMath.TensionState(rawF = 0f, visualE = state.visualE)
        settleRunning = true
        lastFrameNanos = -1L
        if (gen != generation) return
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun dispose() {
        generation += 1
        stopSettleFrames()
        boundBehavior?.attachPhaseOwner(null)
        boundBehavior = null
        state = StretchMath.TensionState()
        host.translationY = 0f
    }
}

/**
 * EdgeEffect: getDistance khac 0 khi edge so huu E — RV reverse/fling ownership.
 * onPullDistance chi tra phan raw da nap; khong goi super (tat native glow).
 * Top chi bao E>0; bottom chi bao E<0 — khong dual.
 */
private class PhaseGatedEdgeEffect(
    context: Context,
    private val controller: PhaseStretchController,
    private val isTop: Boolean,
) : EdgeEffect(context) {

    override fun getDistance(): Float {
        // F0 is already settling its claimed velocity; native fling must not claim it again.
        // ACTION_DOWN reacquires the spring before touch releaseVerticalGlow reads this.
        if (controller.isSettling()) return 0f
        val g = controller.boundG()
        if (g <= StretchMath.VisualEpsilonPx) return 0f
        val e = controller.peekVisualE()
        return when {
            isTop && e > StretchMath.VisualEpsilonPx ->
                (e / g).coerceIn(0f, 1f)
            !isTop && e < -StretchMath.VisualEpsilonPx ->
                ((-e) / g).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    override fun onPull(deltaDistance: Float) {
        onPullDistance(deltaDistance, 0.5f)
    }

    override fun onPull(deltaDistance: Float, displacement: Float) {
        onPullDistance(deltaDistance, displacement)
    }

    override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
        val g = controller.boundG()
        if (g <= StretchMath.VisualEpsilonPx || deltaDistance == 0f) return 0f

        // Tang stretch chi khi phase cho phep; giam/reverse luon (distance>0 path).
        val increasing = deltaDistance > 0f
        if (increasing) {
            if (isTop && !controller.allowTopStretch()) return 0f
            if (!isTop && !controller.allowBottomStretch()) return 0f
        } else {
            // Giam: chi khi edge nay dang so huu tension.
            if (getDistance() <= 0f) return 0f
        }

        // Top: +delta → +E; Bottom: +delta → -E.
        val signedPx = if (isTop) deltaDistance * g else -deltaDistance * g
        val consumedPx = controller.consumePullPx(signedPx, isTop)
        if (consumedPx == 0f) return 0f
        // Tra fraction cung dau deltaDistance, chi phan that su nap.
        val frac = (consumedPx / signedPx) * deltaDistance
        return frac.coerceIn(
            minOf(0f, deltaDistance),
            maxOf(0f, deltaDistance),
        )
    }

    override fun onRelease() {
        controller.release()
    }

    override fun onAbsorb(velocity: Int) {
        controller.absorb(velocity, isTop)
    }

    override fun draw(canvas: Canvas): Boolean = false

    override fun isFinished(): Boolean {
        val e = controller.peekVisualE()
        return if (isTop) e <= StretchMath.VisualEpsilonPx else e >= -StretchMath.VisualEpsilonPx
    }

    override fun finish() {
        // Settle/release so huu — khong clear mid-flight.
    }
}
