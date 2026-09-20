package io.chaldeaprjkt.gamespace.ui.nav

/**
 * Keeps selection progress continuous across loop wraps so the indicator can slide
 * last→first (and first→last) with the pager instead of teleporting after settle.
 */
class ContinuousSelectionProgressHolder {
    var value: Float = 0f
        private set

    fun update(wrapped: Float, settled: Boolean, pageCount: Int): Float {
        val count = pageCount.coerceAtLeast(1).toFloat()
        if (settled) {
            value = wrapped
            return value
        }
        var candidate = wrapped
        while (candidate - value > count / 2f) {
            candidate -= count
        }
        while (value - candidate > count / 2f) {
            candidate += count
        }
        value = candidate
        while (value >= count * 2f) {
            value -= count
        }
        while (value < -count) {
            value += count
        }
        return value
    }
}

fun ContinuousSelectionProgressHolder.updateFromAbsolutePage(
    absolutePage: Int,
    pageOffset: Float,
    settled: Boolean,
    logicalPageCount: Int,
): Float {
    val count = logicalPageCount.coerceAtLeast(1)
    val wrapped = pivotFloorMod(absolutePage + pageOffset, count.toFloat())
    return update(wrapped, settled, count)
}
