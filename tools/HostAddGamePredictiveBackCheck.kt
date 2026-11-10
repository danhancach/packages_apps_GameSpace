/**
 * Host check — Add-game predictive Back ownership (pure).
 * progress/cancel/commit + search priority + stale generation.
 */
package io.chaldeaprjkt.gamespace.tools

import io.chaldeaprjkt.gamespace.ui.motion.AddGamePredictiveBack
import io.chaldeaprjkt.gamespace.ui.motion.MiuixPageMotion
import kotlin.math.abs
import kotlin.system.exitProcess

object HostAddGamePredictiveBackCheck {
    private val fails = ArrayList<String>()

    private fun check(cond: Boolean, msg: String) {
        if (!cond) fails.add(msg)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // --- Ownership: search consumes → submenu khong scrub ---
        check(
            AddGamePredictiveBack.submenuOwnsPredictive(
                desiredOpen = true,
                searchConsumesBack = false,
            ),
            "open + no search → submenu owns predictive",
        )
        check(
            !AddGamePredictiveBack.submenuOwnsPredictive(
                desiredOpen = true,
                searchConsumesBack = true,
            ),
            "search/IME consumes → submenu does not own",
        )
        check(
            !AddGamePredictiveBack.submenuOwnsPredictive(
                desiredOpen = false,
                searchConsumesBack = false,
            ),
            "closed route → no predictive ownership",
        )

        // --- beginSession ---
        check(
            AddGamePredictiveBack.beginSession(false, 1L, 1f) == null,
            "begin ignored when closed",
        )
        check(
            AddGamePredictiveBack.beginSession(true, 0L, 1f) == null,
            "begin ignored when gen=0",
        )
        val session = AddGamePredictiveBack.beginSession(true, 3L, 0.85f)
        check(session != null && session.generation == 3L, "begin captures generation")
        check(session != null && abs(session.anchorPageProgress - 0.85f) < 1e-6f,
            "begin captures open-anchor progress")

        // --- progress scrub from captured anchor ---
        val p20 = AddGamePredictiveBack.scrubPageProgress(session, true, 3L, 0.2f)
        val p70 = AddGamePredictiveBack.scrubPageProgress(session, true, 3L, 0.7f)
        check(p20 != null && abs(p20 - 0.85f * 0.8f) < 1e-5f, "scrub@0.2 from anchor")
        check(p70 != null && p70 < p20!!, "scrub tracks continuous back progress")
        check(
            AddGamePredictiveBack.scrubPageProgress(session, true, 4L, 0.5f) == null,
            "stale generation ignores scrub",
        )
        check(
            AddGamePredictiveBack.scrubPageProgress(session, false, 3L, 0.5f) == null,
            "closed desiredOpen ignores scrub",
        )
        check(
            AddGamePredictiveBack.scrubPageProgress(null, true, 3L, 0.5f) == null,
            "null session ignores scrub",
        )
        check(!AddGamePredictiveBack.scrubAllowsCleanup(), "scrub never cleans child")

        // Mid-scrub at p~0 van khong cleanup (driver contract via motion helper)
        val nearZero = AddGamePredictiveBack.scrubPageProgress(session, true, 3L, 1f)
        check(nearZero != null && abs(nearZero) < 1e-6f, "full gesture maps to 0")
        check(
            !MiuixPageMotion.shouldCleanupChild(nearZero!!, desiredOpen = true, 3L, 3L),
            "preview@0 while desiredOpen keeps child",
        )

        // --- cancel: spring fully open, keep child ---
        check(
            AddGamePredictiveBack.onCancelled(session, true, 3L) ==
                AddGamePredictiveBack.CancelDecision.SpringFullyOpen,
            "cancel → spring fully open",
        )
        check(
            AddGamePredictiveBack.onCancelled(session, true, 9L) ==
                AddGamePredictiveBack.CancelDecision.Ignore,
            "cancel stale gen ignored",
        )
        check(
            AddGamePredictiveBack.onCancelled(session, false, 3L) ==
                AddGamePredictiveBack.CancelDecision.Ignore,
            "cancel after close ignored (no reopen)",
        )
        check(
            AddGamePredictiveBack.onCancelled(null, true, 3L) ==
                AddGamePredictiveBack.CancelDecision.Ignore,
            "cancel without session ignored",
        )
        // Spring from mid-preview must not cleanup
        var cancelP = p70!!
        var cancelV = 0f
        repeat(240) {
            val s = MiuixPageMotion.springStep(cancelP, cancelV, 1f, 1f / 60f)
            cancelP = s.value
            cancelV = s.velocity
        }
        check(abs(cancelP - 1f) < 1e-6f, "cancel spring reaches fully open")
        check(
            !MiuixPageMotion.shouldCleanupChild(cancelP, desiredOpen = true, 3L, 3L),
            "cancel settle keeps fragment/search/list",
        )

        // --- commit: close route; cleanup once after settle + matching gen ---
        check(
            AddGamePredictiveBack.onCommitted(true) ==
                AddGamePredictiveBack.CommitDecision.CloseRoute,
            "commit while open → close route",
        )
        check(
            AddGamePredictiveBack.onCommitted(false) ==
                AddGamePredictiveBack.CommitDecision.Ignore,
            "commit while closed → ignore (no double close)",
        )
        val commitStart = requireNotNull(p70)
        var commitP = commitStart
        var commitV = 0f
        repeat(240) {
            val s = MiuixPageMotion.springStep(commitP, commitV, 0f, 1f / 60f)
            commitP = s.value
            commitV = s.velocity
        }
        check(
            MiuixPageMotion.shouldCleanupChild(commitP, desiredOpen = false, 3L, 3L),
            "commit cleanup exactly once at settled root + matching gen",
        )
        check(
            !MiuixPageMotion.shouldCleanupChild(commitP, desiredOpen = false, 3L, 4L),
            "rapid reopen after commit does not clean new gen",
        )
        check(
            !MiuixPageMotion.shouldCleanupChild(commitStart, desiredOpen = false, 3L, 3L),
            "commit must not clean while still mid-progress",
        )

        // --- invalidate clears ownership ---
        check(AddGamePredictiveBack.invalidateSession() == null, "invalidate → null session")

        // --- LTR/RTL geometry under predictive map (reuse motion) ---
        val w = 1080f
        val preview = AddGamePredictiveBack.scrubPageProgress(session, true, 3L, 0.5f)!!
        val childLtr = MiuixPageMotion.childLayer(preview, w, rtl = false)
        val childRtl = MiuixPageMotion.childLayer(preview, w, rtl = true)
        check(childLtr.translationX > 0f, "LTR predictive reveals parent (+X child)")
        check(childRtl.translationX < 0f, "RTL predictive reveals parent (-X child)")

        // --- Repeated gesture: new session after invalidate ---
        val again = AddGamePredictiveBack.beginSession(true, 3L, 1f)
        check(again != null, "repeated gesture can begin new session")
        check(
            AddGamePredictiveBack.scrubPageProgress(again, true, 3L, 0.3f) != null,
            "new session accepts scrub",
        )

        // --- releaseVelocity: inheritance of gesture dynamics ---
        check(
            AddGamePredictiveBack.releaseVelocity(
                session = null,
                desiredOpen = true,
                currentProgress = 0.5f,
                target = 0f,
                swipeVelocity = -5f,
            ) == null,
            "null session returns null releaseVelocity",
        )
        check(
            AddGamePredictiveBack.releaseVelocity(
                session = again,
                desiredOpen = false,
                currentProgress = 0.5f,
                target = 0f,
                swipeVelocity = -5f,
            ) == null,
            "closed route returns null releaseVelocity",
        )
        val validRelease = AddGamePredictiveBack.releaseVelocity(
            session = again,
            desiredOpen = true,
            currentProgress = 0.5f,
            target = 0f,
            swipeVelocity = -3f,
        )
        check(
            validRelease != null && abs(validRelease - (-3f)) < 1e-5f,
            "valid session computes inherited velocity",
        )

        if (fails.isNotEmpty()) {
            fails.forEach { println("FAIL: $it") }
            println("FAIL HostAddGamePredictiveBackCheck")
            exitProcess(1)
        }
        println(
            "PASS HostAddGamePredictiveBackCheck " +
                "(ownership/search/progress/cancel/commit/stale/ltr-rtl)",
        )
    }
}
