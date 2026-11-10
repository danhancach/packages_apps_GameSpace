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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.view.Choreographer

/**
 * Mot progress owner: tween500 r22 tu nghi, spring(1,146,0.0025) khi interrupt/retarget.
 * Giu van toc qua cancel va ke thua dong hoc vuot (Animatable-equivalent).
 */
class MiuixPageProgressDriver(
    private val onProgress: (Float) -> Unit,
    private val onSettled: (progress: Float, generation: Long) -> Unit,
) {
    var progress: Float = 0f
        private set
    var velocity: Float = 0f
        private set

    private var target: Float = 0f
    private var activeGeneration: Long = 0L
    private var tweenAnimator: ValueAnimator? = null
    private var springCallback: Choreographer.FrameCallback? = null
    private var lastFrameNanos: Long = 0L
    private var lastSampleProgress: Float = 0f
    private var lastSampleNanos: Long = 0L

    private val r22Interpolator = TimeInterpolator { input ->
        MiuixPageMotion.easingR22(input)
    }

    fun snapTo(value: Float, generation: Long) {
        cancelInternal(retainVelocity = false)
        activeGeneration = generation
        target = value.coerceIn(0f, 1f)
        progress = target
        velocity = 0f
        lastSampleProgress = progress
        lastSampleNanos = 0L
        onProgress(progress)
        onSettled(progress, activeGeneration)
    }

    /**
     * Predictive Back so huu layer khi ngon tay dang keo.
     * scrub KHONG goi onSettled (ke ca progress=0) — cleanup chi sau commit settle.
     * Ghi nhan van toc vuot de tiep quan dong hoc khi nha tay.
     */
    fun scrubTo(value: Float, generation: Long) {
        val prevProgress = progress
        val prevNanos = lastSampleNanos
        val now = System.nanoTime()
        cancelInternal(retainVelocity = false)
        activeGeneration = generation
        target = value.coerceIn(0f, 1f)
        progress = target
        if (prevNanos != 0L && now > prevNanos) {
            val dt = (now - prevNanos) / 1_000_000_000f
            if (dt > 0.0001f) {
                velocity = ((progress - prevProgress) / dt).coerceIn(-50f, 50f)
            }
        }
        lastSampleProgress = progress
        lastSampleNanos = now
        onProgress(progress)
    }

    fun animateTo(
        targetValue: Float,
        generation: Long,
        initialVelocity: Float? = null,
        forceSpring: Boolean = false,
    ) {
        val retainedVel = cancelInternal(retainVelocity = true)
        activeGeneration = generation
        target = targetValue.coerceIn(0f, 1f)
        velocity = initialVelocity ?: retainedVel
        if (progress == target && velocity == 0f) {
            onProgress(progress)
            onSettled(progress, activeGeneration)
            return
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            progress = target
            velocity = 0f
            onProgress(progress)
            onSettled(progress, activeGeneration)
            return
        }
        if (!forceSpring && MiuixPageMotion.useFullPageTween(velocity, progress, target)) {
            startTween()
        } else {
            startSpring()
        }
    }

    fun cancel() {
        cancelInternal(retainVelocity = true)
    }

    fun destroy() {
        cancelInternal(retainVelocity = false)
    }

    private fun cancelInternal(retainVelocity: Boolean): Float {
        val retained = if (retainVelocity) estimateVelocity() else 0f
        tweenAnimator?.let { anim ->
            anim.removeAllListeners()
            anim.removeAllUpdateListeners()
            anim.cancel()
        }
        tweenAnimator = null
        springCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        springCallback = null
        lastFrameNanos = 0L
        if (retainVelocity) {
            velocity = retained
        } else {
            velocity = 0f
        }
        return velocity
    }

    private fun estimateVelocity(): Float {
        val now = lastSampleNanos
        if (now == 0L) return velocity
        return velocity
    }

    private fun startTween() {
        val start = progress
        val end = target
        val durationScale = ValueAnimator.getDurationScale().coerceAtLeast(0.01f)
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (MiuixPageMotion.FullPageDurationMs * durationScale).toLong()
            interpolator = r22Interpolator
            addUpdateListener { va ->
                val frac = va.animatedValue as Float
                val next = start + (end - start) * frac
                val nanos = System.nanoTime()
                if (lastSampleNanos != 0L) {
                    val dt = (nanos - lastSampleNanos) / 1_000_000_000f
                    if (dt > 0f) {
                        velocity = (next - lastSampleProgress) / dt
                    }
                }
                lastSampleNanos = nanos
                lastSampleProgress = next
                progress = next
                onProgress(progress)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (tweenAnimator !== animation) return
                    tweenAnimator = null
                    progress = end
                    velocity = 0f
                    lastSampleProgress = progress
                    onProgress(progress)
                    onSettled(progress, activeGeneration)
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (tweenAnimator !== animation) return
                    tweenAnimator = null
                }
            })
        }
        tweenAnimator = anim
        lastSampleNanos = System.nanoTime()
        lastSampleProgress = progress
        anim.start()
    }

    private fun startSpring() {
        lastFrameNanos = 0L
        lastSampleNanos = System.nanoTime()
        lastSampleProgress = progress
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (springCallback !== this) return
                if (!ValueAnimator.areAnimatorsEnabled()) {
                    springCallback = null
                    progress = target
                    velocity = 0f
                    onProgress(progress)
                    onSettled(progress, activeGeneration)
                    return
                }
                val durationScale = ValueAnimator.getDurationScale().coerceAtLeast(0.01f)
                val dtSec = if (lastFrameNanos == 0L) {
                    (1f / 60f) / durationScale
                } else {
                    ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f / durationScale).coerceIn(0.001f, 0.064f)
                }
                lastFrameNanos = frameTimeNanos
                val sample = MiuixPageMotion.springStep(
                    value = progress,
                    velocity = velocity,
                    target = target,
                    dtSec = dtSec,
                )
                progress = sample.value
                velocity = sample.velocity
                lastSampleNanos = frameTimeNanos
                lastSampleProgress = progress
                onProgress(progress)
                if (sample.settled) {
                    springCallback = null
                    progress = target
                    velocity = 0f
                    onProgress(progress)
                    onSettled(progress, activeGeneration)
                } else {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        springCallback = callback
        Choreographer.getInstance().postFrameCallback(callback)
    }
}
