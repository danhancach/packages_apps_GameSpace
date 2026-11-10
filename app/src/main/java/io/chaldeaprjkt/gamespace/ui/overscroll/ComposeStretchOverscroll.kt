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

import android.provider.Settings
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Custom stretch OverscrollEffect cho in-game panel scroll (khong AppBar header).
 * Preference+AppBar dung [MiuixPreferenceScrollCoordinator] (phase Miuix).
 * Place (0, rint(E)) — MOT owner / scroll slot; KHONG dung rememberOverscrollEffect.
 *
 * applyToFling: op token TRUOC child (Updater MiuixFlingOrchestrator) — child cu
 * khong start settle moi.
 */
class GamespaceStretchOverscrollEffect(
    private val readAnimatorScale: () -> Float = { 1f },
) : OverscrollEffect {
    private val stretchNode = StretchLayoutNode(this)

    internal var visualOffset by mutableFloatStateOf(0f)
        private set

    private var rawF = 0f
    private var boundG = 0f
    private var settleJob: Job? = null
    private var flingGeneration = 0L

    override val isInProgress: Boolean
        get() = abs(visualOffset) > StretchMath.VisualEpsilonPx || settleJob?.isActive == true

    override val node: DelegatableNode
        get() = stretchNode

    internal fun onLayoutDetached() {
        flingGeneration += 1
        settleJob?.cancel()
        settleJob = null
        rawF = 0f
        visualOffset = 0f
        boundG = 0f
    }

    internal fun updateBoundG(heightPx: Float) {
        if (heightPx <= 0f) {
            if (boundG != 0f || rawF != 0f || visualOffset != 0f) {
                cancelSettle()
                rawF = 0f
                visualOffset = 0f
                boundG = 0f
            }
            return
        }
        boundG = heightPx
        if (rawF != 0f) {
            rawF = rawF.coerceIn(-boundG, boundG)
            visualOffset = StretchMath.stretchVisual(rawF, boundG)
        }
    }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (boundG <= StretchMath.VisualEpsilonPx) {
            return performScroll(delta)
        }
        val isVerticalUser =
            source == NestedScrollSource.UserInput && delta.y != 0f

        if (isVerticalUser && isInProgress) {
            interruptSettleKeepTension()
        }

        var remaining = delta
        var preReleased = 0f
        if (isVerticalUser && abs(visualOffset) > StretchMath.VisualEpsilonPx) {
            ensureRawF()
            if (rawF != 0f && sign(remaining.y) != sign(rawF)) {
                val (next, consumed) = StretchMath.reverseRelease(
                    StretchMath.TensionState(rawF, visualOffset),
                    remaining.y,
                    boundG,
                )
                rawF = next.rawF
                visualOffset = next.visualE
                preReleased = consumed
                remaining = Offset(remaining.x, remaining.y - consumed)
            }
        }

        val consumedByScroll = performScroll(remaining)
        val leftoverY = remaining.y - consumedByScroll.y

        if (isVerticalUser &&
            abs(visualOffset) > StretchMath.VisualEpsilonPx &&
            consumedByScroll.y != 0f
        ) {
            ensureRawF()
            val tensionSign = sign(
                rawF.takeIf { abs(it) > StretchMath.VisualEpsilonPx } ?: visualOffset,
            )
            val reduce = -tensionSign * abs(consumedByScroll.y)
            val released = StretchMath.reverseRelease(
                StretchMath.TensionState(rawF, visualOffset),
                reduce,
                boundG,
            )
            rawF = released.first.rawF
            visualOffset = released.first.visualE
        }

        var post = 0f
        if (source == NestedScrollSource.UserInput && leftoverY != 0f) {
            val next = StretchMath.applyDelta(
                StretchMath.TensionState(rawF, visualOffset),
                leftoverY,
                boundG,
            )
            rawF = next.rawF
            visualOffset = next.visualE
            post = leftoverY
        }

        return Offset(consumedByScroll.x, consumedByScroll.y + post + preReleased)
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        if (boundG <= StretchMath.VisualEpsilonPx) {
            performFling(velocity)
            return
        }

        val entryE = visualOffset
        val pre = MiuixScrollPhase.flingPreConsumedY(entryE, velocity.y)
        val toChildY = velocity.y - pre
        val f0SettleVy =
            if (abs(entryE) > StretchMath.VisualEpsilonPx) velocity.y else null

        // Op token TRUOC child — khop MiuixFlingOrchestrator.beginOperation.
        val opId = ++flingGeneration
        cancelSettle()

        var childConsumedY = 0f
        try {
            if (f0SettleVy != null) {
                startSettle(f0SettleVy, opId)
            }
            val consumed = performFling(Velocity(velocity.x, toChildY))
            childConsumedY = consumed.y
        } catch (t: Throwable) {
            if (opId == flingGeneration) {
                cancelSettle()
            }
            throw t
        }

        // Obsolete child: gesture moi / detach da invalidate — khong I0.
        if (opId != flingGeneration) return

        cancelSettle()
        val liveE = visualOffset
        val leftoverY = toChildY - childConsumedY
        val i0 = MiuixScrollPhase.flingSettleVelocity(liveE, leftoverY)
        if (i0 != null) {
            startSettle(i0, opId)
        }
    }

    private fun ensureRawF() {
        if (rawF == 0f && abs(visualOffset) > StretchMath.VisualEpsilonPx && boundG > 0f) {
            rawF = StretchMath.inverseStretch(visualOffset, boundG)
        }
    }

    private fun interruptSettleKeepTension() {
        flingGeneration += 1
        val job = settleJob ?: return
        settleJob = null
        job.cancel()
        if (rawF == 0f && abs(visualOffset) > StretchMath.VisualEpsilonPx) {
            rawF = StretchMath.inverseStretch(visualOffset, boundG)
        }
    }

    private fun cancelSettle() {
        settleJob?.cancel()
        settleJob = null
    }

    private fun startSettle(initialVelocity: Float, requiredOp: Long) {
        cancelSettle()
        if (requiredOp != flingGeneration) return
        if (!stretchNode.isAttached) {
            if (abs(visualOffset) <= StretchMath.VisualEpsilonPx) {
                rawF = 0f
                visualOffset = 0f
            }
            return
        }
        val scale = readAnimatorScale()
        if (scale <= 0f) {
            rawF = 0f
            visualOffset = 0f
            return
        }
        if (abs(visualOffset) <= StretchMath.VisualEpsilonPx &&
            abs(initialVelocity) <= StretchMath.VisualEpsilonPx
        ) {
            rawF = 0f
            visualOffset = 0f
            return
        }
        val startE = visualOffset
        val period = StretchMath.settlePeriodSeconds(initialVelocity)
        val gen = requiredOp
        rawF = 0f
        settleJob = stretchNode.coroutineScope.launch {
            try {
                var pos = startE.toDouble()
                var vel = initialVelocity.toDouble()
                var prev = pos
                var lastFrame = -1L
                while (gen == flingGeneration) {
                    val frame = withFrameNanos { it }
                    if (lastFrame < 0L) {
                        lastFrame = frame
                        continue
                    }
                    val dt = ((frame - lastFrame) / 1_000_000_000.0).coerceIn(0.001, 0.016)
                    lastFrame = frame
                    val scaledDt = dt / scale.coerceAtLeast(0.01f).toDouble()
                    val step = StretchMath.settleStep(pos, vel, scaledDt, period)
                    if (StretchMath.shouldClearSettle(step.first, step.second, prev)) {
                        rawF = 0f
                        visualOffset = 0f
                        return@launch
                    }
                    prev = pos
                    pos = step.first
                    vel = step.second
                    visualOffset = pos.toFloat()
                    if (abs(pos) < StretchMath.VisualEpsilonPx &&
                        abs(vel) < StretchMath.VisualEpsilonPx
                    ) {
                        rawF = 0f
                        visualOffset = 0f
                        return@launch
                    }
                }
            } finally {
                if (settleJob === coroutineContext[Job]) {
                    settleJob = null
                }
                // Terminal zero neu gen con so huu va |E| nho.
                if (gen == flingGeneration &&
                    abs(visualOffset) <= StretchMath.VisualEpsilonPx
                ) {
                    rawF = 0f
                    visualOffset = 0f
                }
            }
        }
    }
}

private class StretchLayoutNode(
    private val effect: GamespaceStretchOverscrollEffect,
) : Modifier.Node(), LayoutModifierNode {
    override fun onDetach() {
        effect.onLayoutDetached()
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        effect.updateBoundG(placeable.height.toFloat())
        return layout(placeable.width, placeable.height) {
            val oy = Math.rint(effect.visualOffset.toDouble()).toInt()
            placeable.place(0, oy)
        }
    }
}

@Composable
fun rememberGamespaceStretchOverscroll(): GamespaceStretchOverscrollEffect {
    val context = LocalContext.current
    return remember {
        GamespaceStretchOverscrollEffect {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ).coerceAtLeast(0f)
        }
    }
}
