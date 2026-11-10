package io.chaldeaprjkt.gamespace.ui.nav

/**
 * Continuous nav selection for finite tabs: page + offset.
 * Matches Updater selectedPagePosition = currentPage + currentPageOffsetFraction.
 * No loop-wrap / modulo — Library↔Settings stays bounded 0↔1.
 */
class ContinuousSelectionProgressHolder {
    var value: Float = 0f
        private set

    fun update(page: Int, pageOffset: Float): Float {
        value = page + pageOffset
        return value
    }
}
