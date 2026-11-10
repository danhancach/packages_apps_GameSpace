package io.chaldeaprjkt.gamespace.ui.motion

import kotlin.math.PI
import kotlin.math.abs

/**
 * Miuix compact title motion (Folme Spring HyperOS/MIUI).
 *
 * SSOT: .docs/ui/chuyen-dong-va-hoat-hoa-motion.md muc 3 va 8.
 * - largeTitle mo het trong 1/3 hanh trinh cuon dau tien (collapsedFraction * 3f >= 1f).
 * - smallTitleVisible kich hoat khi collapsedFraction * 3f >= 1f.
 * - Folme Spring bat doi xung thoi gian:
 *     Show (cuon len): damping = 1.0f, response = 0.3f -> stiffness = (2*PI/0.3)^2 approx 438.65f.
 *                      Troi tu Y = +20px -> 0px va Alpha = 0 -> 1.
 *     Hide (cuon xuong): damping = 1.0f, response = 0.15f -> stiffness = (2*PI/0.15)^2 approx 1754.6f.
 *                      Troi tu Y = 0px -> +20px va Alpha = 1 -> 0 (nhanh gap doi de tranh de chu).
 */
object MiuixTitleMotion {
    // Khoang cach troi doc cua small title: 20px
    const val TransitionDistancePx = 20f

    // Tham so Folme Spring theo cong thuc: stiffness = (2 * PI / response)^2
    const val ResponseShow = 0.3f
    const val ResponseHide = 0.15f
    const val DampingRatio = 1.0f

    const val StiffnessShow = 438.6491f
    const val StiffnessHide = 1754.5964f
    const val Threshold = 0.001f

    // Ty le cuon 1/3 de kich hoat small title
    const val CollapseThreshold = 1f / 3f

    fun startInset(contentInsetPx: Float, navigationExtentPx: Float, gapPx: Float): Float =
        maxOf(contentInsetPx, if (navigationExtentPx > 0f) navigationExtentPx + gapPx else 0f)

    /**
     * Vi tri X bat dau chu tieu de nho theo canh quang hoc nut back Expressive 40dp
     * (khong dung touch-target ImageButton co the >40dp), + gap AOSP ~22dp.
     * Do AOSP Settings: circleRight → titleStart ≈ 22.6dp (lay 22dp chuan).
     * Khi hasNavigation=true: gapPx da bao gom toan bo khoang cach tu canh phai vong back
     * den chu, tuyet doi khong cong don titleMarginStartPx de tranh double margin.
     */
    fun collapsedTitleStartX(
        toolbarLeftPx: Float,
        navCenterXPx: Float,
        hasNavigation: Boolean,
        backIconSizePx: Float,
        gapPx: Float,
        contentInsetStartPx: Float,
        titleMarginStartPx: Float,
    ): Float {
        return if (hasNavigation && backIconSizePx > 0f) {
            navCenterXPx + backIconSizePx / 2f + gapPx
        } else {
            toolbarLeftPx + contentInsetStartPx + titleMarginStartPx
        }
    }

    /**
     * Tinh collapsed fraction tu offset cuon hien tai (offsetPx am) va tong khoang cuon.
     */
    fun collapseFraction(offsetPx: Float, totalScrollRangePx: Float): Float =
        if (totalScrollRangePx > 0f) (abs(offsetPx) / totalScrollRangePx).coerceIn(0f, 1f) else 0f

    /**
     * Nguong kich hoat tieu de nho: chi hien khi tieu de lon da mo hoan toan (1/3 cuon).
     */
    fun isSmallTitleVisible(collapseFraction: Float): Boolean =
        (collapseFraction * 3f) >= 1f

    /**
     * Cong thuc lam mo tieu de lon: mo het trong 1/3 hanh trinh dau tien.
     */
    fun largeTitleAlpha(collapseFraction: Float): Float =
        (1f - (collapseFraction * 3f)).coerceIn(0f, 1f)

    fun stiffness(visible: Boolean): Float =
        if (visible) StiffnessShow else StiffnessHide

    /**
     * Folme Spring buoc tiep theo cho alpha (0..1).
     */
    fun alphaStep(
        value: Float,
        velocity: Float,
        visible: Boolean,
        dtSec: Float,
    ): MiuixPageMotion.SpringSample {
        val target = if (visible) 1f else 0f
        val k = if (visible) StiffnessShow else StiffnessHide
        return MiuixPageMotion.springStep(
            value, velocity, target, dtSec, k, Threshold,
        )
    }

    /**
     * Folme Spring buoc tiep theo cho translationY (20px -> 0px khi hien, 0px -> 20px khi an).
     */
    fun translationYStep(
        value: Float,
        velocity: Float,
        visible: Boolean,
        dtSec: Float,
    ): MiuixPageMotion.SpringSample {
        val target = if (visible) 0f else TransitionDistancePx
        val k = if (visible) StiffnessShow else StiffnessHide
        return MiuixPageMotion.springStep(
            value, velocity, target, dtSec, k, Threshold,
        )
    }

    /**
     * Buoc spring tong quat voi stiffness tuy y.
     */
    fun springStep(
        value: Float,
        velocity: Float,
        target: Float,
        dtSec: Float,
        stiffness: Float = StiffnessShow,
        threshold: Float = Threshold,
    ): MiuixPageMotion.SpringSample =
        MiuixPageMotion.springStep(value, velocity, target, dtSec, stiffness, threshold)
}
