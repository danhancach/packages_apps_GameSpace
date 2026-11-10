/**
 * Host check — MiuixPageMotion ownership / geometry / spring settle (pure).
 * KHONG assert hang so copy-paste thuan; kiem tra hanh vi.
 */
package io.chaldeaprjkt.gamespace.tools

import io.chaldeaprjkt.gamespace.ui.motion.MiuixPageMotion
import kotlin.math.abs
import kotlin.system.exitProcess

object HostMiuixPageMotionCheck {
    private val fails = ArrayList<String>()

    private fun check(cond: Boolean, msg: String) {
        if (!cond) fails.add(msg)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // r22: endpoints + monotonic-ish mid
        check(abs(MiuixPageMotion.easingR22(0f) - 0f) < 1e-5f, "r22(0)=0")
        check(abs(MiuixPageMotion.easingR22(1f) - 1f) < 2e-2f, "r22(1)~1")
        val mid = MiuixPageMotion.easingR22(0.5f)
        check(mid > 0.5f && mid < 1f, "r22(0.5) past linear got $mid")

        // Spec branch: rest full-page vs retarget spring
        check(
            MiuixPageMotion.useFullPageTween(0f, 0f, 1f),
            "rest 0→1 uses tween",
        )
        check(
            MiuixPageMotion.useFullPageTween(0f, 1f, 0f),
            "rest 1→0 uses tween",
        )
        check(
            !MiuixPageMotion.useFullPageTween(0.4f, 0.2f, 1f),
            "nonzero velocity → spring",
        )
        check(
            !MiuixPageMotion.useFullPageTween(0f, 0.5f, 1f),
            "partial delta → spring",
        )

        // Geometry LTR at p=0 / p=1 / p=0.5
        val w = 1080f
        val child0 = MiuixPageMotion.childLayer(0f, w, rtl = false)
        check(abs(child0.translationX - w) < 0.5f, "LTR child@0 = W")
        check(abs(child0.alpha - 1f) < 1e-6f, "child alpha 1")
        val child1 = MiuixPageMotion.childLayer(1f, w, rtl = false)
        check(abs(child1.translationX) < 0.5f, "LTR child@1 = 0")
        val parentHalf = MiuixPageMotion.parentLayer(0.5f, w, rtl = false)
        check(
            abs(parentHalf.translationX - (-w * 0.5f * 0.25f)) < 1f,
            "LTR parent@0.5 = -W/8 got ${parentHalf.translationX}",
        )
        check(
            abs(parentHalf.alpha - (1f - 0.1f * 0.5f)) < 1e-5f,
            "parent alpha 1-0.1p",
        )

        // RTL: one sign inversion on X
        val childRtl = MiuixPageMotion.childLayer(0f, w, rtl = true)
        check(abs(childRtl.translationX - (-w)) < 0.5f, "RTL child@0 = -W")
        val parentRtl = MiuixPageMotion.parentLayer(1f, w, rtl = true)
        check(
            abs(parentRtl.translationX - (w * 0.25f)) < 1f,
            "RTL parent@1 = +W/4 got ${parentRtl.translationX}",
        )

        // Scrim scales with p
        check(abs(MiuixPageMotion.scrimAlpha(0f)) < 1e-6f, "scrim@0")
        check(
            abs(MiuixPageMotion.scrimAlpha(1f) - MiuixPageMotion.NavScrimMaxAlpha) < 1e-6f,
            "scrim@1",
        )

        // Spring settles toward target (ownership of progress value)
        var v = 0.2f
        var vel = 0f
        var settled = false
        repeat(240) {
            val s = MiuixPageMotion.springStep(v, vel, target = 1f, dtSec = 1f / 60f)
            v = s.value
            vel = s.velocity
            settled = s.settled
            if (settled) return@repeat
        }
        check(settled, "spring reaches settle toward 1")
        check(abs(v - 1f) < MiuixPageMotion.SettleThreshold, "spring value ~1 got $v")

        // Cleanup identity: rapid reopen must not cleanup new route
        check(
            MiuixPageMotion.shouldCleanupChild(0f, desiredOpen = false, 3L, 3L),
            "cleanup when gen match + closed",
        )
        check(
            !MiuixPageMotion.shouldCleanupChild(0f, desiredOpen = false, 3L, 4L),
            "no cleanup when gen stale",
        )
        check(
            !MiuixPageMotion.shouldCleanupChild(0f, desiredOpen = true, 4L, 4L),
            "no cleanup while desired open",
        )
        check(
            !MiuixPageMotion.shouldCleanupChild(0.5f, desiredOpen = false, 4L, 4L),
            "no cleanup mid progress",
        )

        // f3: restore ownership — mid-pop fragment must FinishClose, not SnapOpen
        check(
            MiuixPageMotion.restoreRouteAction(false, hasAddGameFragment = false) ==
                MiuixPageMotion.RestoreRouteAction.Root,
            "restore root when closed + no fragment",
        )
        check(
            MiuixPageMotion.restoreRouteAction(true, hasAddGameFragment = true) ==
                MiuixPageMotion.RestoreRouteAction.SnapOpen,
            "restore SnapOpen when desired open",
        )
        check(
            MiuixPageMotion.restoreRouteAction(true, hasAddGameFragment = false) ==
                MiuixPageMotion.RestoreRouteAction.SnapOpen,
            "restore SnapOpen even if fragment not yet restored",
        )
        check(
            MiuixPageMotion.restoreRouteAction(false, hasAddGameFragment = true) ==
                MiuixPageMotion.RestoreRouteAction.FinishClose,
            "restore FinishClose mid-pop (open=false + fragment)",
        )
        // Rapid open/close/recreate: FinishClose + matching gen → cleanup; stale gen → no
        check(
            MiuixPageMotion.shouldCleanupChild(0f, desiredOpen = false, 5L, 5L),
            "FinishClose settle cleans matching gen",
        )
        check(
            !MiuixPageMotion.shouldCleanupChild(0f, desiredOpen = false, 5L, 6L),
            "rapid reopen after FinishClose snap does not clean new gen",
        )

        // f2: parent input block vs visual-bounds at root
        check(
            MiuixPageMotion.shouldBlockParentInput(desiredOpen = false) == false,
            "root: no parent block (visual-bounds)",
        )
        check(
            MiuixPageMotion.shouldBlockParentInput(desiredOpen = true),
            "opening desired: block parent",
        )
        check(
            !MiuixPageMotion.shouldBlockParentInput(desiredOpen = false),
            "committed close: revealed Add game accepts reopen before settle",
        )
        check(
            MiuixPageMotion.shouldBlockParentInput(desiredOpen = false)
                == false,
            "at settle threshold + closed: unblock",
        )
        check(
            MiuixPageMotion.shouldBlockParentInput(desiredOpen = true),
            "fully open: block parent (scrim owns overlay; nav independent via layer order)",
        )

        // Predictive map
        check(
            abs(MiuixPageMotion.pageProgressFromPredictiveBack(0f, 0.8f) - 0.8f) < 1e-6f,
            "predictive@0=anchor",
        )
        check(
            abs(MiuixPageMotion.pageProgressFromPredictiveBack(1f, 0.8f)) < 1e-6f,
            "predictive@1=0",
        )
        val anchor = 0.8f // Back started while an open animation is still running.
        val firstPreview = MiuixPageMotion.pageProgressFromPredictiveBack(0.2f, anchor)
        val nextPreview = MiuixPageMotion.pageProgressFromPredictiveBack(0.7f, anchor)
        check(abs(firstPreview - 0.64f) < 1e-6f, "preview follows captured anchor")
        check(nextPreview < firstPreview, "preview tracks continuous back progress")
        check(MiuixPageMotion.scrimAlpha(nextPreview) < MiuixPageMotion.scrimAlpha(firstPreview),
            "preview fades scrim continuously")
        check(MiuixPageMotion.childLayer(nextPreview, w, rtl = false).translationX >
            MiuixPageMotion.childLayer(firstPreview, w, rtl = false).translationX,
            "preview reveals parent in LTR")
        check(MiuixPageMotion.childLayer(nextPreview, w, rtl = true).translationX <
            MiuixPageMotion.childLayer(firstPreview, w, rtl = true).translationX,
            "preview reveals parent in RTL")
        check(abs(MiuixPageMotion.pageProgressFromPredictiveBack(-0.2f, anchor) - anchor) < 1e-6f,
            "negative gesture progress clamps")
        check(MiuixPageMotion.pageProgressFromPredictiveBack(1.2f, anchor) == 0f,
            "gesture progress beyond 1 clamps")

        // Cancel: a spring returns from the last preview without closing the fragment.
        var cancelProgress = nextPreview
        var cancelVelocity = 0f
        var cancelSettled = false
        repeat(240) {
            val sample = MiuixPageMotion.springStep(cancelProgress, cancelVelocity, 1f, 1f / 60f)
            cancelProgress = sample.value
            cancelVelocity = sample.velocity
            if (sample.settled) cancelSettled = true
        }
        check(cancelSettled && abs(cancelProgress - 1f) < 1e-6f,
            "cancel springs back to fully open")
        check(!MiuixPageMotion.shouldCleanupChild(cancelProgress, true, 6L, 6L),
            "cancel does not remove child")

        // Commit: animation only cleans the child once the closed route reaches zero.
        check(!MiuixPageMotion.shouldCleanupChild(nextPreview, false, 6L, 6L),
            "commit does not clean child while preview is partial")
        var commitProgress = nextPreview
        var commitVelocity = 0f
        repeat(240) {
            val sample = MiuixPageMotion.springStep(commitProgress, commitVelocity, 0f, 1f / 60f)
            commitProgress = sample.value
            commitVelocity = sample.velocity
        }
        check(MiuixPageMotion.shouldCleanupChild(commitProgress, false, 6L, 6L),
            "commit cleans child after closing settle")

        // Predictive release velocity (LSPosed y12.java)
        // Khi remaining > 0 (commit dong ve 0): minVelocity = -sqrt(146) * remaining
        val sqrt146 = kotlin.math.sqrt(146f)
        val relRemaining = 0.5f
        val expectedMinVel = -sqrt146 * relRemaining
        val fastFlingClose = MiuixPageMotion.predictiveReleaseVelocity(
            currentProgress = 0.5f,
            target = 0f,
            savedSwipeVelocity = -2f,
        )
        check(abs(fastFlingClose - (-2f)) < 1e-5f, "savedSwipeVelocity > minVelocity returns saved")
        val slowDragClose = MiuixPageMotion.predictiveReleaseVelocity(
            currentProgress = 0.5f,
            target = 0f,
            savedSwipeVelocity = -20f,
        )
        check(abs(slowDragClose - expectedMinVel) < 1e-5f, "savedSwipeVelocity < minVelocity clamps to minVelocity")

        // Khi remaining <= 0 (cancel tro lai 1f): minVelocity = 0f
        val cancelForward = MiuixPageMotion.predictiveReleaseVelocity(
            currentProgress = 0.5f,
            target = 1f,
            savedSwipeVelocity = 3f,
        )
        check(abs(cancelForward - 3f) < 1e-5f, "cancel positive velocity preserved")
        val cancelBackward = MiuixPageMotion.predictiveReleaseVelocity(
            currentProgress = 0.5f,
            target = 1f,
            savedSwipeVelocity = -2f,
        )
        check(abs(cancelBackward - 0f) < 1e-5f, "cancel negative velocity clamped to 0")

        if (fails.isNotEmpty()) {
            fails.forEach { println("FAIL: $it") }
            println("FAIL HostMiuixPageMotionCheck")
            exitProcess(1)
        }
        println("PASS HostMiuixPageMotionCheck (geometry/tween/spring/cleanup/restore/block/predictive)")
    }
}
