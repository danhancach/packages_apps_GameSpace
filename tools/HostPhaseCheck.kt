/**
 * Host check — compile cung StretchMath + MiuixScrollPhase + NavIndicatorGeometry.
 * Khong Android SDK. Chay: tools/host-check-miuix-phase.sh
 */
package io.chaldeaprjkt.gamespace.tools

import io.chaldeaprjkt.gamespace.ui.nav.NavIndicatorGeometry
import io.chaldeaprjkt.gamespace.ui.overscroll.MiuixScrollPhase
import io.chaldeaprjkt.gamespace.ui.overscroll.StretchMath
import kotlin.math.abs
import kotlin.system.exitProcess

object HostPhaseCheck {
    private val fails = ArrayList<String>()

    private fun check(cond: Boolean, msg: String) {
        if (!cond) fails.add(msg)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val g = 1000f

        // --- StretchMath (source that) ---
        val eMid = StretchMath.stretchVisual(500f, g)
        check(eMid > 0f && eMid < 500f, "stretchVisual mid")
        val eMax = StretchMath.stretchVisual(g, g)
        check(abs(eMax - g / 3f) < 1e-3f, "stretchVisual(G,G)=G/3 got $eMax")
        for (f in floatArrayOf(100f, 400f, 900f, -250f)) {
            val e = StretchMath.stretchVisual(f, g)
            val back = StretchMath.inverseStretch(e, g)
            check(abs(back - f) < 1.5f, "inverse roundtrip f=$f back=$back")
        }
        val (revState, revC) = StretchMath.reverseRelease(
            StretchMath.TensionState(300f, StretchMath.stretchVisual(300f, g)),
            -200f,
            g,
        )
        check(revC < 0f, "reverseRelease consumed direction")
        check(abs(revState.rawF) < 300f, "reverseRelease reduces |rawF|")

        // Settle convergence — terminal zero (f3)
        val (finalPos, steps) = StretchMath.settleUntilZero(
            startPos = 80.0,
            startVel = 0.0,
            period = StretchMath.SettlePeriodNormal,
            scale = 1.0,
        )
        check(finalPos == 0.0, "settleUntilZero must terminal 0 got $finalPos steps=$steps")
        val (snapPos, _) = StretchMath.settleUntilZero(40.0, 100.0, 0.4, scale = 0.0)
        check(snapPos == 0.0, "scale=0 snap zero")
        // Miuix §3G: Non-oscillating terminal condition
        check(
            StretchMath.shouldClearSettle(pos = -1.2, vel = -10.0, prevPos = 2.0),
            "shouldClearSettle must stop immediately on crossed even if |pos| >= 1.0",
        )
        check(
            StretchMath.shouldClearSettle(pos = 0.5, vel = 0.2, prevPos = 0.8),
            "shouldClearSettle stops when within epsilon bounds",
        )
        check(
            !StretchMath.shouldClearSettle(pos = 5.0, vel = -2.0, prevPos = 6.0),
            "shouldClearSettle continues when away from target without crossing",
        )

        // --- Phase pre/post (source that, f1) ---
        val cMid = MiuixScrollPhase.capsFromViewMetrics(
            appBarTop = -20,
            totalScrollRange = 100,
            contentUpRemaining = 0f,
            contentDownRemaining = 500f,
            tensionE = 0f,
            boundG = g,
        )
        check(abs(cMid.headerCollapseRemaining - 80f) < 0.1f, "collapse rem 80")
        val pre = MiuixScrollPhase.preScroll(-100f, cMid)
        check(abs(pre.headerConsumed - (-80f)) < 0.1f, "pre collapse-first header=${pre.headerConsumed}")
        check(abs(pre.toChild - (-20f)) < 0.1f, "pre toChild=${pre.toChild}")
        check(!cMid.canTopStretch, "no top stretch while collapsing")

        val cAtTop = MiuixScrollPhase.capsFromViewMetrics(
            appBarTop = -100,
            totalScrollRange = 100,
            contentUpRemaining = 0f,
            contentDownRemaining = 200f,
            tensionE = 0f,
            boundG = g,
        )
        val postExpand = MiuixScrollPhase.postScroll(50f, cAtTop)
        check(abs(postExpand.headerConsumed - 50f) < 0.1f, "post expand before stretch")
        check(postExpand.stretchConsumed == 0f, "no stretch while expand rem")

        val cExp = MiuixScrollPhase.capsFromViewMetrics(
            appBarTop = 0,
            totalScrollRange = 100,
            contentUpRemaining = 0f,
            contentDownRemaining = 200f,
            tensionE = 0f,
            boundG = g,
        )
        check(cExp.canTopStretch, "canTopStretch expanded+top")
        val postStretch = MiuixScrollPhase.postScroll(30f, cExp)
        check(postStretch.headerConsumed == 0f, "no header when expanded")
        check(abs(postStretch.stretchConsumed) > 0f, "stretch claims leftover raw")

        val cShort = MiuixScrollPhase.capsFromViewMetrics(
            appBarTop = 0,
            totalScrollRange = 100,
            contentUpRemaining = 0f,
            contentDownRemaining = 0f,
            tensionE = 0f,
            boundG = g,
        )
        check(!cShort.canContentScroll, "short !canContent")
        val postShort = MiuixScrollPhase.postScroll(-25f, cShort)
        check(abs(postShort.stretchConsumed) > 0f, "short stretch")

        // Reverse tension in pre before header
        val eTen = StretchMath.stretchVisual(200f, g)
        val cTen = MiuixScrollPhase.capsFromViewMetrics(
            appBarTop = 0,
            totalScrollRange = 100,
            contentUpRemaining = 0f,
            contentDownRemaining = 500f,
            tensionE = eTen,
            boundG = g,
        )
        val preRev = MiuixScrollPhase.preScroll(-150f, cTen)
        check(preRev.tensionConsumed < 0f, "pre reverse tension first")

        // Fling handoff constants (source that)
        check(
            abs(MiuixScrollPhase.flingPreConsumedY(50f, 100f) - 100f) < 1e-3f,
            "F0 same-sign full",
        )
        val opp = MiuixScrollPhase.flingPreConsumedY(50f, -100f)
        check(
            abs(opp - (-100f / MiuixScrollPhase.PreFlingOppositeConsumeDivisor)) < 1e-3f,
            "F0 opposite /2.13333",
        )
        val i0 = MiuixScrollPhase.flingSettleVelocity(10f, 30f)
        check(
            i0 != null && abs(i0 - 30f / MiuixScrollPhase.PostFlingVelocityDivisor) < 1e-3f,
            "I0 leftover/1.53333",
        )
        check(MiuixScrollPhase.flingSettleVelocity(10f, 0f) == 0f, "I0 E+leftover0 → 0")
        check(MiuixScrollPhase.flingSettleVelocity(0f, 0f) == null, "I0 idle null")

        // Edge distance ownership model (f2 math): top only +E, bottom only -E
        fun distTop(e: Float, bound: Float) =
            if (e > StretchMath.VisualEpsilonPx) (e / bound).coerceIn(0f, 1f) else 0f
        fun distBottom(e: Float, bound: Float) =
            if (e < -StretchMath.VisualEpsilonPx) ((-e) / bound).coerceIn(0f, 1f) else 0f
        check(distTop(100f, g) > 0f && distBottom(100f, g) == 0f, "no dual top/bottom +E")
        check(distTop(-100f, g) == 0f && distBottom(-100f, g) > 0f, "no dual top/bottom -E")

        // Partial reverse consume leaves remainder (f2 onPullDistance)
        val (partial, consumedPartial) = StretchMath.reverseRelease(
            StretchMath.TensionState(100f, StretchMath.stretchVisual(100f, g)),
            -250f,
            g,
        )
        check(partial.rawF == 0f && partial.visualE == 0f, "over-reverse clears E")
        check(abs(consumedPartial - (-100f)) < 1e-3f, "remainder not claimed by reverse")

        // --- Nav geometry (source that) ---
        val colN = NavIndicatorGeometry.equalColumnWidthPx(floatArrayOf(72f, 80f), 48f)
        check(colN == 80f, "narrow equal col")
        val colL = NavIndicatorGeometry.equalColumnWidthPx(floatArrayOf(72f, 160f), 48f, 400f)
        check(colL == 160f, "large equal col")
        val colB = NavIndicatorGeometry.equalColumnWidthPx(floatArrayOf(72f, 160f), 48f, 200f)
        check(abs(colB - 100f) < 1e-3f, "bounded equal col")

        if (fails.isNotEmpty()) {
            println("FAIL")
            fails.forEach { println(" - $it") }
            exitProcess(1)
        }
        println("PASS HostPhaseCheck (StretchMath+MiuixScrollPhase+NavIndicatorGeometry sources)")
        println("  settleUntilZero steps=$steps terminal=0")
        println("  pre collapse-first header=${pre.headerConsumed} toChild=${pre.toChild}")
        println("  fling F0/I0 + edge distance ownership OK")
    }
}
