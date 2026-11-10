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
package io.chaldeaprjkt.gamespace.ui.gesture

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.slider.Slider
import io.chaldeaprjkt.gamespace.ui.gesture.PagerAxisOwnership.Axis
import io.chaldeaprjkt.gamespace.ui.overscroll.MiuixPreferenceScrollCoordinator

/**
 * Bao ViewPager2 — classify axis TRUOC/SAU dispatch theo hop dong ViewGroup.
 *
 * ViewGroup xoa FLAG_DISALLOW_INTERCEPT o ACTION_DOWN → phai restore AFTER
 * super(DOWN). Vertical/Undecided giu disallow qua MOVE; UP/CANCEL release AFTER.
 *
 * Slider/track: DOWN trung Material [Slider] (hoac child requestDisallow trong
 * chuoi) → childHorizontalClaim — khong release pager khi axis Horizontal.
 * Horizontal PRE-MOVE khong release som: child trong vertical RV (Slider) chi
 * claim o MOVE; release chi POST khi !claim.
 *
 * Han che da biet: neu pager da settling tu gesture ngang truoc do, DOWN moi
 * khong huy settle do — chi chan intercept gesture hien tai.
 * Khong disable userInput global; khong an tap / PopupMenu.
 */
class ViewPager2AxisGuard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val config = PagerAxisOwnership.Config(
        touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        dominantRatio = 1.0f,
    )
    private var ownership = PagerAxisOwnership.reset()

    /** Chan re-entry khi minh goi requestDisallow tren RV (bubble len day). */
    private var applyingDisallow = false

    /**
     * Da POST-release cho Horizontal && !claim trong chuoi nay.
     * Frame dau Horizontal: PRE van khoa de Slider kip claim; frame sau PRE nhả
     * de ViewPager2 intercept (tranh defer mai mai).
     */
    private var horizontalReleaseArmed = false

    /**
     * Da thong bao triet tieu tension truc doc sang coordinator trong chuoi cu chi nay.
     * Miuix §4A.3 / confirmHorizontalTakeoverClearTension.
     */
    private var horizontalTakeoverReported = false

    private fun pager(): ViewPager2? {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c is ViewPager2) return c
        }
        return null
    }

    private fun pagerRecycler(): RecyclerView? =
        pager()?.getChildAt(0) as? RecyclerView

    /**
     * Khoa intercept ngang cua ViewPager2 internal RV.
     * Goi tren chinh RV → FLAG_DISALLOW_INTERCEPT chan onInterceptTouchEvent.
     */
    private fun applyDisallowForAxis(axis: Axis) {
        val rv = pagerRecycler() ?: return
        applyingDisallow = true
        try {
            when (axis) {
                Axis.Vertical, Axis.Undecided -> {
                    rv.requestDisallowInterceptTouchEvent(true)
                    (rv.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                }
                Axis.Horizontal -> {
                    rv.requestDisallowInterceptTouchEvent(false)
                }
            }
        } finally {
            applyingDisallow = false
        }
    }

    private fun applyDisallowFromOwnership(preDispatch: Boolean) {
        when {
            !ownership.active -> {
                horizontalReleaseArmed = false
                applyDisallowForAxis(Axis.Horizontal)
            }
            // Claim / Vertical / Undecided: luon khoa pager; huy arm neu vua claim.
            PagerAxisOwnership.wantsPagerDisallow(ownership) -> {
                horizontalReleaseArmed = false
                applyDisallowForAxis(Axis.Vertical)
            }
            else -> {
                // Horizontal && !claim — SSOT gate (one-frame defer).
                val (disallow, nextArmed) = PagerAxisOwnership.disallowGateStep(
                    ownership,
                    horizontalReleaseArmed,
                    preDispatch,
                )
                horizontalReleaseArmed = nextArmed
                applyDisallowForAxis(if (disallow) Axis.Vertical else Axis.Horizontal)
            }
        }
    }

    /**
     * Hit-test view hierarchy: DOWN tren Material Slider (thumb/track widget),
     * khong hardcode toa do. +/- / title nam ngoai Slider → khong claim.
     */
    private fun sliderOwnsAt(ev: MotionEvent): Boolean {
        val p = pager() ?: return false
        val x = ev.x - p.left + p.scrollX
        val y = ev.y - p.top + p.scrollY
        if (x < 0f || y < 0f || x >= p.width || y >= p.height) return false
        val deepest = findDeepestChildAt(p, x, y) ?: return false
        return isMaterialSlider(deepest)
    }

    private fun findDeepestChildAt(group: ViewGroup, x: Float, y: Float): View? {
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val cx = x - child.left + child.scrollX
            val cy = y - child.top + child.scrollY
            if (cx < 0f || cy < 0f || cx >= child.width || cy >= child.height) continue
            if (child is ViewGroup) {
                val deeper = findDeepestChildAt(child, cx, cy)
                if (deeper != null) return deeper
            }
            return child
        }
        return null
    }

    private fun isMaterialSlider(view: View): Boolean {
        var cur: Class<*>? = view.javaClass
        while (cur != null) {
            val n = cur.name
            if (n == Slider::class.java.name ||
                n == "com.google.android.material.slider.BaseSlider" ||
                n == "com.google.android.material.slider.RangeSlider"
            ) {
                return true
            }
            cur = cur.superclass
        }
        return false
    }

    /** POINTER_UP: pointer con lai (khac ended), neu co. */
    private fun successorPointer(ev: MotionEvent, endedId: Int): Triple<Int, Float, Float>? {
        val n = ev.pointerCount
        for (i in 0 until n) {
            val id = ev.getPointerId(i)
            if (id != endedId) {
                return Triple(id, ev.getX(i), ev.getY(i))
            }
        }
        return null
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Chi latch claim khi huong di chuyen hien tai la ngang (Slider ngang claim).
        // Khong coi cuon doc cua RecyclerView con la slider claim lam te liet cuon tab ngang!
        if (disallowIntercept && ownership.active && !applyingDisallow) {
            if (ownership.axis == Axis.Horizontal) {
                ownership = PagerAxisOwnership.claimChildHorizontal(ownership)
            }
            applyDisallowForAxis(Axis.Vertical)
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked

        // --- Pre-dispatch: cap nhat ownership; MOVE set disallow truoc intercept ---
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                horizontalReleaseArmed = false
                horizontalTakeoverReported = false
                val sliderClaim = sliderOwnsAt(ev)
                ownership = PagerAxisOwnership.down(
                    ownership,
                    ev.getPointerId(0),
                    ev.x,
                    ev.y,
                    childHorizontalClaim = sliderClaim,
                )
                // Truoc DOWN: co the set, nhung ViewGroup se xoa — restore o post.
                applyDisallowForAxis(Axis.Vertical)
            }
            MotionEvent.ACTION_MOVE -> {
                if (ownership.active) {
                    val idx = ev.findPointerIndex(ownership.pointerId)
                    if (idx >= 0) {
                        ownership = PagerAxisOwnership.move(
                            ownership,
                            ownership.pointerId,
                            ev.getX(idx),
                            ev.getY(idx),
                            config,
                        )
                        // Cu chi ngang tiep quan: triet tieu tension truc doc (Miuix §4A.3)
                        if (PagerAxisOwnership.locksHorizontal(ownership) && !horizontalTakeoverReported) {
                            horizontalTakeoverReported = true
                            MiuixPreferenceScrollCoordinator.confirmHorizontalTakeoverClearTension()
                        }
                    }
                    // Index mat: giu ownership (khong unlock ngang).
                    // PRE: khong release Horizontal som (mayReleasePre = false).
                    applyDisallowFromOwnership(preDispatch = true)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val endedId = ev.getPointerId(ev.actionIndex)
                val succ = successorPointer(ev, endedId)
                ownership = PagerAxisOwnership.pointerEnded(
                    ownership,
                    endedId,
                    succ?.first,
                    succ?.second ?: 0f,
                    succ?.third ?: 0f,
                )
                applyDisallowFromOwnership(preDispatch = true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Reset ownership sau delivery — post phase release flag.
            }
        }

        val handled = super.dispatchTouchEvent(ev)

        // --- Post-dispatch: restore sau DOWN clear; release sau UP/CANCEL ---
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // Bat buoc: ViewGroup da xoa flag trong super(DOWN).
                if (PagerAxisOwnership.wantsPagerDisallow(ownership)) {
                    applyDisallowForAxis(Axis.Vertical)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Sau child claim (neu co): Horizontal && !claim moi release.
                applyDisallowFromOwnership(preDispatch = false)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                applyDisallowFromOwnership(preDispatch = false)
            }
            MotionEvent.ACTION_UP -> {
                ownership = PagerAxisOwnership.up(ownership, ev.getPointerId(0))
                horizontalReleaseArmed = false
                horizontalTakeoverReported = false
                applyDisallowForAxis(Axis.Horizontal)
            }
            MotionEvent.ACTION_CANCEL -> {
                ownership = PagerAxisOwnership.cancel(ownership)
                horizontalReleaseArmed = false
                horizontalTakeoverReported = false
                applyDisallowForAxis(Axis.Horizontal)
            }
        }

        return handled
    }

    override fun onDetachedFromWindow() {
        ownership = PagerAxisOwnership.reset()
        horizontalReleaseArmed = false
        horizontalTakeoverReported = false
        applyDisallowForAxis(Axis.Horizontal)
        super.onDetachedFromWindow()
    }
}
