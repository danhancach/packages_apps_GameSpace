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
package io.chaldeaprjkt.gamespace.ui.nav

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Geometry pill indicator cho compact nav (equal columns theo label rong nhat).
 * Pure — host check fontScale/narrow label.
 */
object NavIndicatorGeometry {
    data class PillFrame(val xPx: Float, val widthPx: Float)

    /**
     * Cot deu = max(minTouch, naturalWidths); bound boi availableWidth neu > 0.
     */
    fun equalColumnWidthPx(
        naturalWidthsPx: FloatArray,
        minTouchPx: Float,
        availableWidthPx: Float = Float.POSITIVE_INFINITY,
    ): Float {
        require(naturalWidthsPx.isNotEmpty())
        val widest = naturalWidthsPx.maxOrNull()!!.coerceAtLeast(minTouchPx)
        val count = naturalWidthsPx.size
        if (!availableWidthPx.isFinite() || availableWidthPx <= 0f) return widest
        val maxPerCol = availableWidthPx / count
        return widest.coerceAtMost(maxPerCol.coerceAtLeast(minTouchPx))
    }

    fun foldProgress(progress: Float, tabCount: Int): Float {
        val count = tabCount.coerceAtLeast(1).toFloat()
        if (!progress.isFinite()) return 0f
        val min = -1f
        val max = count + 1f
        var folded = progress
        val span = count
        if (folded >= max) {
            folded -= floor((folded - min) / span) * span
        }
        if (folded < min) {
            folded += floor((max - folded - 0.0001f) / span) * span
        }
        while (folded >= max) folded -= span
        while (folded < min) folded += span
        return folded
    }

    /**
     * Pill frames (LTR). RTL: caller lat x = totalWidth - x - width.
     * Wrap loop: them pill phu khi folded ngoai [0, count).
     */
    fun pillFrames(
        foldedProgress: Float,
        tabCount: Int,
        columnWidthPx: Float,
        rtl: Boolean = false,
    ): List<PillFrame> {
        if (columnWidthPx <= 0f || tabCount <= 0) return emptyList()
        val count = tabCount.toFloat()
        val offsets = buildList {
            add(foldedProgress)
            if (foldedProgress > count - 1f) add(foldedProgress - count)
            if (foldedProgress < 0f) add(foldedProgress + count)
        }
        val total = columnWidthPx * tabCount
        return offsets.map { p ->
            val x = columnWidthPx * p
            val frame = PillFrame(xPx = x, widthPx = columnWidthPx)
            if (!rtl) frame
            else PillFrame(xPx = total - x - columnWidthPx, widthPx = columnWidthPx)
        }
    }

    fun pillXRounded(foldedProgress: Float, columnWidthPx: Float): Int =
        (columnWidthPx * foldedProgress).roundToInt()
}
