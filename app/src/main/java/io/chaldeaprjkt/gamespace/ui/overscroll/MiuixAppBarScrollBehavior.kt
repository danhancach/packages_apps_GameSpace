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
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.roundToInt

/**
 * Nested-scroll owner live: reverse tension → header → list (pre);
 * list leftover → expand → edge stretch (post).
 *
 * F0/I0 velocity handoff belongs to MiuixPreferenceScrollCoordinator.
 * Wired XML: app:layout_behavior tren app_bar.
 */
class MiuixAppBarScrollBehavior : AppBarLayout.Behavior {

    constructor() : super()
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Volatile
    private var phaseOwner: PhaseScrollOwner? = null

    fun attachPhaseOwner(owner: PhaseScrollOwner?) {
        phaseOwner = owner
    }

    fun phaseOwnerOrNull(): PhaseScrollOwner? = phaseOwner

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        val owner = phaseOwner
        if (owner == null || dy == 0) {
            super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
            return
        }

        // Live call-site: MiuixScrollPhase.preScroll
        val caps = owner.liveCaps().let {
            if (type == ViewCompat.TYPE_TOUCH) it else it.copy(tensionE = 0f)
        }
        // View dy > 0 scrolls upward; Compose/Miuix delta has the opposite sign.
        val pre = MiuixScrollPhase.preScroll(-dy.toFloat(), caps)
        val tensionDy = (-pre.tensionConsumed).roundToInt()
            .coerceIn(minOf(0, dy), maxOf(0, dy))
        if (tensionDy != 0) {
            owner.commitTension(pre.nextRawF, pre.nextTensionE)
        }

        val afterTension = dy - tensionDy
        val headerConsumed = IntArray(2)
        if (afterTension != 0) {
            // Header collapse (AppBar) — pha 2; phan con lai → child list.
            super.onNestedPreScroll(
                coordinatorLayout,
                child,
                target,
                dx,
                afterTension,
                headerConsumed,
                type,
            )
        }
        // AppBar assigns its output array. Do not let it overwrite tension consumption.
        consumed[0] += headerConsumed[0]
        consumed[1] += tensionDy + headerConsumed[1]
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        val owner = phaseOwner
        if (owner == null || dyUnconsumed == 0) {
            super.onNestedScroll(
                coordinatorLayout,
                child,
                target,
                dxConsumed,
                dyConsumed,
                dxUnconsumed,
                dyUnconsumed,
                type,
                consumed,
            )
            return
        }

        val headerConsumed = IntArray(2)
        super.onNestedScroll(
            coordinatorLayout, child, target, dxConsumed, dyConsumed,
            dxUnconsumed, dyUnconsumed, type, headerConsumed,
        )
        consumed[0] += headerConsumed[0]
        consumed[1] += headerConsumed[1]
        val remainingDy = dyUnconsumed - headerConsumed[1]
        // Non-touch leftovers must reach RecyclerView's real velocity absorb callback.
        // Turning every fling frame into a drag would interrupt the running spring.
        if (remainingDy == 0 || type != ViewCompat.TYPE_TOUCH) return

        val post = MiuixScrollPhase.postScroll(-remainingDy.toFloat(), owner.liveCaps())
        // Claim only the edge phase after the real header has consumed its share.
        if (post.headerConsumed != 0f || post.stretchConsumed == 0f) return
        val stretchDy = (-post.stretchConsumed).roundToInt()
            .coerceIn(minOf(0, remainingDy), maxOf(0, remainingDy))
        if (stretchDy != 0) {
            owner.commitTension(post.nextRawHint, post.nextTensionE)
            consumed[1] += stretchDy
        }
    }
}

/**
 * Bridge Behavior ↔ PhaseStretchController (tranh circular file dep).
 */
interface PhaseScrollOwner {
    fun liveCaps(): MiuixScrollPhase.Caps
    fun commitTension(rawF: Float, visualE: Float)
}
