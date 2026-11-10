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
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.withSign

/**
 * Stretch math subordinate (port rut gon tu Updater MiuixStretchMath /
 * MiuixStretchReacquire). Dung sau phase header↔content — khong phai muc tieu scroll.
 * Pure Kotlin — host check khong can Android build.
 *
 * g(t)=t - t^2 + t^3/3; E = sign(F)*g(|F|/G)*G; F clamp [-G,G].
 */
object StretchMath {
    const val VisualEpsilonPx = 1.0f

    /** |v| > 5000 → period 0.55, else 0.4 (od3). */
    const val SettlePeriodFastVelocity = 5000.0
    const val SettlePeriodHighVelocity = 0.55
    const val SettlePeriodNormal = 0.4

    fun stretchVisual(rawF: Float, boundG: Float): Float {
        if (rawF == 0f || boundG <= 0f) return 0f
        val t = (abs(rawF) / boundG).coerceIn(0f, 1f).toDouble()
        val g = t - t.pow(2.0) + t.pow(3.0) / 3.0
        return (sign(rawF) * g * boundG).toFloat()
    }

    fun inverseStretch(visualE: Float, boundG: Float): Float {
        if (visualE == 0f || boundG <= 0f) return 0f
        val maxVisual = abs(stretchVisual(boundG, boundG))
        val clamped = abs(visualE).coerceAtMost(maxVisual).toDouble()
        val d3 = boundG.toDouble() - clamped * 3.0
        val rawAbs =
            boundG.toDouble() -
                abs(d3).pow(1.0 / 3.0) * sign(d3) * boundG.toDouble().pow(2.0 / 3.0)
        return rawAbs.toFloat().withSign(visualE)
    }

    fun maxDragVisual(boundG: Float): Float = abs(stretchVisual(boundG, boundG))

    data class TensionState(
        val rawF: Float = 0f,
        val visualE: Float = 0f,
    )

    /** Nap delta keo (UserInput leftover). */
    fun applyDelta(state: TensionState, delta: Float, boundG: Float): TensionState {
        if (delta == 0f || boundG <= VisualEpsilonPx) return state
        val f = (state.rawF + delta).coerceIn(-boundG, boundG)
        val e = stretchVisual(f, boundG)
        // Retain subpixel drag input; repeated slow deltas must still accumulate.
        return TensionState(f, e)
    }

    /**
     * Reverse tension doi dau: tra (state moi, luong delta da dung).
     * Dung khi keo nguoc luc dang stretch.
     */
    fun reverseRelease(
        state: TensionState,
        deltaY: Float,
        boundG: Float,
    ): Pair<TensionState, Float> {
        if (deltaY == 0f || boundG <= VisualEpsilonPx) return state to 0f
        if (abs(state.visualE) <= VisualEpsilonPx && abs(state.rawF) <= VisualEpsilonPx) {
            return state to 0f
        }
        val tensionSign = when {
            abs(state.rawF) > VisualEpsilonPx -> sign(state.rawF)
            else -> sign(state.visualE)
        }
        if (sign(deltaY) == tensionSign) {
            // Cung dau: nap them stretch
            return applyDelta(state, deltaY, boundG) to deltaY
        }
        // Doi dau: giam tension
        val toZero = -state.rawF
        return if (abs(deltaY) <= abs(toZero) + VisualEpsilonPx) {
            applyDelta(state, deltaY, boundG) to deltaY
        } else {
            // Het tension, phan con lai khong claim o day
            TensionState() to toZero
        }
    }

    fun settlePeriodSeconds(velocity: Float): Double =
        if (abs(velocity.toDouble()) > SettlePeriodFastVelocity) {
            SettlePeriodHighVelocity
        } else {
            SettlePeriodNormal
        }

    /**
     * Mot buoc od3 settle (host-testable).
     * @return Pair(pos, vel); ve 0 khi crossed/damped.
     */
    fun settleStep(
        pos: Double,
        vel: Double,
        dt: Double,
        period: Double,
    ): Pair<Double, Double> {
        val omega = (Math.PI * 2.0) / period
        val dampA = 2.0 * omega
        val stiffB = omega * omega
        val target = 0.0
        val clampedDt = dt.coerceIn(0.001, 0.016)
        val nextVel = vel * (1.0 - dampA * clampedDt) + (target - pos) * stiffB * clampedDt
        val nextPos = pos + nextVel * clampedDt
        return nextPos to nextVel
    }

    fun shouldClearSettle(pos: Double, vel: Double, prevPos: Double): Boolean {
        val target = 0.0
        val crossed =
            (prevPos > target && pos <= target) ||
                (prevPos < target && pos >= target)
        if (crossed) return true
        if (abs(pos - target) < VisualEpsilonPx && abs(vel) < VisualEpsilonPx) return true
        return false
    }

    /**
     * Chay settleStep den zero (host-testable). scale=1: dt wall; scale<=0 snap zero.
     * @return Pair(finalPos, steps); finalPos phai ~0.
     */
    fun settleUntilZero(
        startPos: Double,
        startVel: Double,
        period: Double,
        scale: Double = 1.0,
        maxSteps: Int = 600,
        dt: Double = 1.0 / 60.0,
    ): Pair<Double, Int> {
        if (scale <= 0.0) return 0.0 to 0
        var pos = startPos
        var vel = startVel
        var prev = pos
        val scaledDt = (dt / scale.coerceAtLeast(0.01)).coerceIn(0.001, 0.016)
        for (i in 1..maxSteps) {
            val step = settleStep(pos, vel, scaledDt, period)
            if (shouldClearSettle(step.first, step.second, prev)) {
                return 0.0 to i
            }
            prev = pos
            pos = step.first
            vel = step.second
        }
        // Terminal cleanup bat buoc — khong de |E|>1 treo.
        return 0.0 to maxSteps
    }
}
