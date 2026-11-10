package io.chaldeaprjkt.gamespace.tools

import io.chaldeaprjkt.gamespace.ui.motion.MiuixTitleMotion
import kotlin.math.PI
import kotlin.math.abs

object HostMiuixTitleMotionCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        // 1. startInset (legacy) van dung cho fallback
        check(MiuixTitleMotion.startInset(8f, 48f, 24f) == 72f)
        check(MiuixTitleMotion.startInset(72f, 48f, 24f) == 72f)
        check(MiuixTitleMotion.startInset(8f, 0f, 24f) == 8f)

        // 1b. collapsedTitleStartX — canh quang hoc 40dp + gap 22dp chuan AOSP
        // toolbarLeft=8, navCenter=8+8+20=36 (margin ngoai, pad8, ban kinh 20), back=40, gap=22
        // Khi hasNavigation=true, titleStartX = 36 + 20 + 22 = 78 (bo qua titleMarginStartPx)
        check(
            MiuixTitleMotion.collapsedTitleStartX(
                toolbarLeftPx = 8f,
                navCenterXPx = 36f,
                hasNavigation = true,
                backIconSizePx = 40f,
                gapPx = 22f,
                contentInsetStartPx = 72f,
                titleMarginStartPx = 16f,
            ) == 78f,
        )
        // Touch-target 56dp, vong 40dp o tam: center=8+8+28=44 → 44+20+22=86 (khong bi cong them titleMarginStartPx)
        check(
            MiuixTitleMotion.collapsedTitleStartX(
                toolbarLeftPx = 8f,
                navCenterXPx = 44f,
                hasNavigation = true,
                backIconSizePx = 40f,
                gapPx = 22f,
                contentInsetStartPx = 72f,
                titleMarginStartPx = 16f,
            ) == 86f,
        )
        // Fallback khi hasNavigation=false: toolbarLeft + contentInsetStart + titleMarginStart = 8 + 72 + 16 = 96
        check(
            MiuixTitleMotion.collapsedTitleStartX(
                toolbarLeftPx = 8f,
                navCenterXPx = 0f,
                hasNavigation = false,
                backIconSizePx = 40f,
                gapPx = 22f,
                contentInsetStartPx = 72f,
                titleMarginStartPx = 16f,
            ) == 96f,
        )

        // 2. collapseFraction
        check(MiuixTitleMotion.collapseFraction(0f, 100f) == 0f)
        check(MiuixTitleMotion.collapseFraction(-50f, 100f) == 0.5f)
        check(MiuixTitleMotion.collapseFraction(-100f, 100f) == 1f)
        check(MiuixTitleMotion.collapseFraction(-150f, 100f) == 1f)
        check(MiuixTitleMotion.collapseFraction(-50f, 0f) == 0f)

        // 3. Nguong kich hoat tieu de nho: chi hien khi tieu de lon mo het o 1/3 hanh trinh
        check(!MiuixTitleMotion.isSmallTitleVisible(0f))
        check(!MiuixTitleMotion.isSmallTitleVisible(0.2f))
        check(!MiuixTitleMotion.isSmallTitleVisible(0.33f))
        check(MiuixTitleMotion.isSmallTitleVisible(1f / 3f))
        check(MiuixTitleMotion.isSmallTitleVisible(0.5f))
        check(MiuixTitleMotion.isSmallTitleVisible(1f))

        // 4. Cong thuc largeTitleAlpha
        check(MiuixTitleMotion.largeTitleAlpha(0f) == 1f)
        check(abs(MiuixTitleMotion.largeTitleAlpha(1f / 6f) - 0.5f) < 0.001f)
        check(MiuixTitleMotion.largeTitleAlpha(1f / 3f) == 0f)
        check(MiuixTitleMotion.largeTitleAlpha(1f) == 0f)

        // 5. Folme Spring formulas
        val expectedShowK = ((2.0 * PI / 0.3) * (2.0 * PI / 0.3)).toFloat()
        val expectedHideK = ((2.0 * PI / 0.15) * (2.0 * PI / 0.15)).toFloat()
        check(abs(MiuixTitleMotion.StiffnessShow - expectedShowK) < 0.01f)
        check(abs(MiuixTitleMotion.StiffnessHide - expectedHideK) < 0.01f)
        check(abs(MiuixTitleMotion.StiffnessHide - MiuixTitleMotion.StiffnessShow * 4f) < 0.01f)

        // 6. Alpha step dynamics
        // Show: chay tu 0 len 1
        var aVal = 0f
        var aVel = 0f
        var showSteps = 0
        while (aVal < 0.99f && showSteps < 120) {
            val s = MiuixTitleMotion.alphaStep(aVal, aVel, visible = true, 1f / 60f)
            aVal = s.value
            aVel = s.velocity
            showSteps++
        }
        check(aVal >= 0.99f)
        repeat(60) {
            val s = MiuixTitleMotion.alphaStep(aVal, aVel, visible = true, 1f / 60f)
            aVal = s.value
            aVel = s.velocity
        }
        check(aVal == 1f && aVel == 0f)

        // Hide: chay tu 1 xuong 0
        var hideSteps = 0
        while (aVal > 0.01f && hideSteps < 120) {
            val s = MiuixTitleMotion.alphaStep(aVal, aVel, visible = false, 1f / 60f)
            aVal = s.value
            aVel = s.velocity
            hideSteps++
        }
        check(aVal <= 0.01f)
        // Bat doi xung thoi gian: Hide (150ms) phai nhanh hon Show (300ms) dang ke
        check(hideSteps < showSteps)
        repeat(60) {
            val s = MiuixTitleMotion.alphaStep(aVal, aVel, visible = false, 1f / 60f)
            aVal = s.value
            aVel = s.velocity
        }
        check(aVal == 0f && aVel == 0f)

        // 7. TranslationY step dynamics
        // Show: troi tu 20px ve 0px
        var yVal = MiuixTitleMotion.TransitionDistancePx
        var yVel = 0f
        repeat(60) {
            val s = MiuixTitleMotion.translationYStep(yVal, yVel, visible = true, 1f / 60f)
            yVal = s.value
            yVel = s.velocity
        }
        check(yVal == 0f && yVel == 0f)

        // Hide: troi tu 0px xuong 20px
        repeat(60) {
            val s = MiuixTitleMotion.translationYStep(yVal, yVel, visible = false, 1f / 60f)
            yVal = s.value
            yVel = s.velocity
        }
        check(yVal == MiuixTitleMotion.TransitionDistancePx && yVel == 0f)

        // 8. Reversal giua chung giu van toc va chuyen huong muot ma
        var revVal = 0f
        var revVel = 0f
        repeat(5) {
            val s = MiuixTitleMotion.alphaStep(revVal, revVel, visible = true, 1f / 60f)
            revVal = s.value
            revVel = s.velocity
        }
        check(revVal > 0f && revVel > 0f)
        repeat(60) {
            val s = MiuixTitleMotion.alphaStep(revVal, revVel, visible = false, 1f / 60f)
            revVal = s.value
            revVel = s.velocity
        }
        check(revVal == 0f && revVel == 0f)

        println("PASS HostMiuixTitleMotionCheck (Folme Spring show/hide asymmetry, 1/3 threshold, 20px translation, largeTitle fade)")
    }
}
