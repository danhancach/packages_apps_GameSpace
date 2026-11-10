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

import kotlin.math.abs
import kotlin.math.sign

/**
 * Phase ownership Miuix/Updater → GameSpace View Preference+AppBar.
 *
 * Goi live tu [MiuixAppBarScrollBehavior] (pre/post/fling handoff), khong mo phong.
 *
 * Map (Updater MiuixHeaderContentScroll / LSPosed li0+ni0 ExitUntilCollapsed):
 *   PreScroll:  1) reverse tension (R0) → 2) header collapse (li0.R0)
 *   Child:      content list scroll
 *   PostScroll: 3) header expand (li0.D0) → 4) short/edge stretch neu !canContentScroll
 *   Fling:      F0/I0 qua [flingPreConsumedY]/[flingSettleVelocity]
 *
 * dy < 0 = cuon len → collapse header truoc, roi list
 * dy > 0 = cuon xuong → list ve top roi expand header
 * Stretch la pha PHU.
 */
object MiuixScrollPhase {
    const val EpsilonPx = StretchMath.VisualEpsilonPx

    /** Fling handoff (Updater MiuixFlingHandoff) — stretch subordinate. */
    const val PostFlingVelocityDivisor = 1.53333f
    const val PreFlingOppositeConsumeDivisor = 2.13333f

    data class Caps(
        val headerCollapseRemaining: Float,
        val headerExpandRemaining: Float,
        val contentUpRemaining: Float,
        val contentDownRemaining: Float,
        val tensionE: Float = 0f,
        val boundG: Float = 0f,
    ) {
        val canContentScroll: Boolean
            get() = contentUpRemaining > EpsilonPx || contentDownRemaining > EpsilonPx

        val headerFullyExpanded: Boolean
            get() = headerExpandRemaining <= EpsilonPx

        val headerFullyCollapsed: Boolean
            get() = headerCollapseRemaining <= EpsilonPx

        val canTopStretch: Boolean
            get() = headerFullyExpanded && contentUpRemaining <= EpsilonPx

        val canBottomStretch: Boolean
            get() = contentDownRemaining <= EpsilonPx
    }

    data class PreResult(
        val tensionConsumed: Float,
        val headerConsumed: Float,
        val toChild: Float,
        val nextTensionE: Float,
        val nextRawF: Float,
        val nextCollapseRemaining: Float,
    ) {
        val claimed: Float get() = tensionConsumed + headerConsumed
    }

    data class PostResult(
        val headerConsumed: Float,
        val stretchConsumed: Float,
        val nextExpandRemaining: Float,
        val nextTensionE: Float,
        val nextRawHint: Float,
    ) {
        val claimed: Float get() = headerConsumed + stretchConsumed
    }

    /**
     * PreScroll owner — khop MiuixHeaderContentScroll.onPreScroll.
     * Chi reverse tension + collapse header; KHONG stretch o Pre.
     */
    fun preScroll(availableY: Float, caps: Caps): PreResult {
        var remaining = availableY
        var tensionClaim = 0f
        var nextE = caps.tensionE
        var nextRaw = if (abs(caps.tensionE) > EpsilonPx && caps.boundG > EpsilonPx) {
            StretchMath.inverseStretch(caps.tensionE, caps.boundG)
        } else {
            0f
        }
        var collapseLeft = caps.headerCollapseRemaining.coerceAtLeast(0f)

        if (remaining != 0f && abs(caps.tensionE) > EpsilonPx && caps.boundG > EpsilonPx) {
            val (next, consumed) = StretchMath.reverseRelease(
                StretchMath.TensionState(nextRaw, caps.tensionE),
                remaining,
                caps.boundG,
            )
            if (sign(remaining) != sign(caps.tensionE) && consumed != 0f) {
                tensionClaim = consumed
                remaining -= consumed
                nextE = next.visualE
                nextRaw = next.rawF
            }
        }

        if (remaining == 0f) {
            return PreResult(tensionClaim, 0f, 0f, nextE, nextRaw, collapseLeft)
        }

        var headerClaim = 0f
        if (remaining < 0f && collapseLeft > EpsilonPx) {
            val take = maxOf(remaining, -collapseLeft)
            headerClaim = take
            collapseLeft += take
            remaining -= take
        }

        return PreResult(
            tensionConsumed = tensionClaim,
            headerConsumed = headerClaim,
            toChild = remaining,
            nextTensionE = nextE,
            nextRawF = nextRaw,
            nextCollapseRemaining = collapseLeft.coerceAtLeast(0f),
        )
    }

    /**
     * PostScroll owner — header expand tu available; stretch short/edge neu cho phep.
     */
    fun postScroll(availableY: Float, caps: Caps, allowStretch: Boolean = true): PostResult {
        var remaining = availableY
        var expandLeft = caps.headerExpandRemaining.coerceAtLeast(0f)
        var headerClaim = 0f
        var stretchClaim = 0f
        var nextE = caps.tensionE
        var nextRaw = 0f

        if (remaining > 0f && expandLeft > EpsilonPx && caps.contentUpRemaining <= EpsilonPx) {
            val take = minOf(remaining, expandLeft)
            headerClaim = take
            expandLeft -= take
            remaining -= take
        }

        if (allowStretch && remaining != 0f && caps.boundG > EpsilonPx) {
            val wantTop = remaining > 0f && caps.canTopStretch
            val wantBottom = remaining < 0f && caps.canBottomStretch
            val shortAll = !caps.canContentScroll
            if (wantTop || wantBottom || shortAll) {
                val state = StretchMath.TensionState(
                    rawF = if (caps.tensionE != 0f) {
                        StretchMath.inverseStretch(caps.tensionE, caps.boundG)
                    } else {
                        0f
                    },
                    visualE = caps.tensionE,
                )
                val beforeRaw = state.rawF
                val next = StretchMath.applyDelta(state, remaining, caps.boundG)
                val actual = next.rawF - beforeRaw
                stretchClaim = actual
                nextE = next.visualE
                nextRaw = next.rawF
                remaining -= actual
            }
        }

        return PostResult(
            headerConsumed = headerClaim,
            stretchConsumed = stretchClaim,
            nextExpandRemaining = expandLeft.coerceAtLeast(0f),
            nextTensionE = nextE,
            nextRawHint = nextRaw,
        )
    }

    fun flingPreConsumedY(visualE: Float, flingVy: Float): Float {
        if (abs(visualE) <= EpsilonPx || flingVy == 0f) return 0f
        return if (sign(flingVy) == sign(visualE)) {
            flingVy
        } else {
            flingVy / PreFlingOppositeConsumeDivisor
        }
    }

    fun flingSettleVelocity(visualE: Float, leftoverY: Float): Float? = when {
        leftoverY != 0f -> leftoverY / PostFlingVelocityDivisor
        abs(visualE) > EpsilonPx -> 0f
        else -> null
    }

    fun capsFromViewMetrics(
        appBarTop: Int,
        totalScrollRange: Int,
        contentUpRemaining: Float,
        contentDownRemaining: Float,
        tensionE: Float,
        boundG: Float,
    ): Caps {
        val range = totalScrollRange.coerceAtLeast(0).toFloat()
        val collapsedAmount = (-appBarTop).toFloat().coerceIn(0f, range)
        return Caps(
            headerCollapseRemaining = (range - collapsedAmount).coerceAtLeast(0f),
            headerExpandRemaining = collapsedAmount,
            contentUpRemaining = contentUpRemaining.coerceAtLeast(0f),
            contentDownRemaining = contentDownRemaining.coerceAtLeast(0f),
            tensionE = tensionE,
            boundG = boundG,
        )
    }
}
