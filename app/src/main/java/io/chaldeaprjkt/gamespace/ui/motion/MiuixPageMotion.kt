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
package io.chaldeaprjkt.gamespace.ui.motion

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Miuix submenu page motion (LSPosed Manager nav / Updater MiuixPageTransition).
 *
 * Provenance: .docs/ui/lsposed-miuix-rea.md muc 4B; .logs/lsposed-miuix-extracted-20261108;
 * Updater MiuixPageTransition.kt — tween500 r22 / spring(1,146,0.0025) / geometry W*(1-p), -W/4.
 * KHONG tab snap spring / Popup spring / Activity fade.
 */
object MiuixPageMotion {
    const val FullPageDurationMs = 500
    const val SettleDampingRatio = 1f
    const val SettleStiffness = 146f
    const val SettleThreshold = 0.0025f
    const val ParentFadeFactor = 0.1f
    const val ParentParallaxFactor = 0.25f
    /** Updater q12/ub26 scrim giua parent va child. */
    const val NavScrimMaxAlpha = 0.5f
    const val FullPageDeltaThreshold = 0.999f

    // r22: giu thu tu lam tron Float tu manager.apk
    private val easingG = -7.4612823f
    private val easingH = (sqrt(24.05716559930144) / 2.0).toFloat()
    private val easingI = easingG / easingH

    data class LayerTransform(
        val translationX: Float,
        val alpha: Float,
    )

    data class SpringSample(
        val value: Float,
        val velocity: Float,
        val settled: Boolean,
    )

    /** r22 easing — t = fraction thoi gian 0..1 cua 500ms. */
    fun easingR22(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f).toDouble()
        return ((sin(easingH * t) * easingI - cos(easingH * t)) * exp(easingG * t) + 1.0)
            .toFloat()
    }

    /**
     * Full-page tu nghi: velocity == 0 va abs(target-current) >= 0.999f -> tween500.
     * Con lai -> spring settle/retarget (giu van toc).
     */
    fun useFullPageTween(velocity: Float, current: Float, target: Float): Boolean =
        velocity == 0f && kotlin.math.abs(target - current) >= FullPageDeltaThreshold

    fun childLayer(progress: Float, widthPx: Float, rtl: Boolean): LayerTransform {
        val p = progress.coerceIn(0f, 1f)
        val direction = if (rtl) -1f else 1f
        val dx = if (widthPx <= 0f) {
            0f
        } else {
            (direction * widthPx * (1f - p)).roundToInt().toFloat()
        }
        return LayerTransform(translationX = dx, alpha = 1f)
    }

    fun parentLayer(progress: Float, widthPx: Float, rtl: Boolean): LayerTransform {
        val p = progress.coerceIn(0f, 1f)
        val direction = if (rtl) -1f else 1f
        val dx = if (widthPx <= 0f) {
            0f
        } else {
            (-direction * widthPx * p * ParentParallaxFactor).roundToInt().toFloat()
        }
        return LayerTransform(
            translationX = dx,
            alpha = 1f - ParentFadeFactor * p,
        )
    }

    fun scrimAlpha(progress: Float): Float =
        NavScrimMaxAlpha * progress.coerceIn(0f, 1f)

    /**
     * Critically-damped spring (Compose mass=1, dampingRatio=1) toi target.
     * stiffness=146, visibilityThreshold=0.0025 theo so trang.
     */
    fun springStep(
        value: Float,
        velocity: Float,
        target: Float,
        dtSec: Float,
        stiffness: Float = SettleStiffness,
        threshold: Float = SettleThreshold,
    ): SpringSample {
        if (dtSec <= 0f) {
            return SpringSample(value, velocity, settled = false)
        }
        val omega = sqrt(stiffness.coerceAtLeast(0f))
        val x0 = value - target
        if (kotlin.math.abs(x0) < threshold && kotlin.math.abs(velocity) < threshold) {
            return SpringSample(target, 0f, settled = true)
        }
        // Critically damped: (A + B t) e^(-omega t)
        val t = dtSec
        val envelope = exp((-omega * t).toDouble()).toFloat()
        val b = velocity + omega * x0
        val displacement = (x0 + b * t) * envelope
        val newValue = target + displacement
        val newVelocity = (velocity - omega * b * t) * envelope
        val settled =
            kotlin.math.abs(newValue - target) < threshold &&
                kotlin.math.abs(newVelocity) < threshold
        return if (settled) {
            SpringSample(target, 0f, settled = true)
        } else {
            SpringSample(newValue, newVelocity, settled = false)
        }
    }

    /**
     * Cleanup child chi khi settled ve root VA generation van khop
     * (tranh rapid reopen xoa fragment cua route moi).
     */
    fun shouldCleanupChild(
        settledProgress: Float,
        desiredOpen: Boolean,
        completedGeneration: Long,
        currentGeneration: Long,
    ): Boolean =
        settledProgress <= SettleThreshold &&
            !desiredOpen &&
            completedGeneration == currentGeneration &&
            currentGeneration != 0L

    /**
     * Recreate ownership: chi SnapOpen khi saved desiredOpen=true.
     * open=false + con fragment (mid-pop) -> FinishClose, KHONG resurrect.
     */
    enum class RestoreRouteAction {
        Root,
        SnapOpen,
        FinishClose,
    }

    fun restoreRouteAction(
        savedDesiredOpen: Boolean,
        hasAddGameFragment: Boolean,
    ): RestoreRouteAction =
        when {
            savedDesiredOpen -> RestoreRouteAction.SnapOpen
            hasAddGameFragment -> RestoreRouteAction.FinishClose
            else -> RestoreRouteAction.Root
        }

    /**
     * Block parent khi dang mo / da mo, bao gom ca cu chi predictive back chua commit.
     * Khi dong da commit, phan parent lo ra nhan cham ngay lap tuc;
     * child van nhan touch trong vung visual bounds cua no.
     */
    fun shouldBlockParentInput(desiredOpen: Boolean): Boolean = desiredOpen

    /**
     * Predictive Back linear map (Updater APPROX):
     * backProgress=0 -> anchor; backProgress=1 -> 0.
     */
    fun pageProgressFromPredictiveBack(backProgress: Float, anchorPageProgress: Float): Float {
        val t = backProgress.coerceIn(0f, 1f)
        val anchor = anchorPageProgress.coerceIn(0f, 1f)
        return anchor * (1f - t)
    }

    /**
     * Tinh van toc dau khi tha tay cu chi Predictive Back (y12.java tu LSPosed Manager).
     *
     * remaining = current - target
     * minVelocity = remaining > 0 ? -sqrt(146) * remaining : 0.0f
     * initialVelocity = max(savedSwipeVelocity, minVelocity)
     */
    fun predictiveReleaseVelocity(
        currentProgress: Float,
        target: Float,
        savedSwipeVelocity: Float,
        stiffness: Float = SettleStiffness,
    ): Float {
        val remaining = currentProgress - target
        val minVelocity = if (remaining > 0f) {
            -sqrt(stiffness.coerceAtLeast(0f)) * remaining
        } else {
            0f
        }
        return if (savedSwipeVelocity < minVelocity) minVelocity else savedSwipeVelocity
    }
}
