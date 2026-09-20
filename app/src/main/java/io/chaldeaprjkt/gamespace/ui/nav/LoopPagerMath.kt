package io.chaldeaprjkt.gamespace.ui.nav

import kotlin.math.abs

/** Positive modulo for looping pagers / segment tabs. */
fun pivotFloorMod(value: Int, modulus: Int): Int {
    if (modulus <= 0) return 0
    return ((value % modulus) + modulus) % modulus
}

fun pivotFloorMod(value: Float, modulus: Float): Float {
    if (modulus <= 0f) return 0f
    val result = value % modulus
    return if (result < 0f) result + modulus else result
}

/**
 * Nearest absolute pager index for [targetLogical] on a looping pager, preferring the
 * shorter swipe (forward when distances are equal).
 */
fun nearestLoopPage(
    currentPage: Int,
    targetLogical: Int,
    pageCount: Int,
): Int {
    if (pageCount <= 0) return currentPage
    val currentLogical = pivotFloorMod(currentPage, pageCount)
    val target = pivotFloorMod(targetLogical, pageCount)
    var delta = target - currentLogical
    if (delta > pageCount / 2) delta -= pageCount
    if (delta < -(pageCount / 2)) delta += pageCount
    if (pageCount % 2 == 0 && abs(delta) == pageCount / 2 && delta < 0) {
        delta = pageCount / 2
    }
    return currentPage + delta
}
