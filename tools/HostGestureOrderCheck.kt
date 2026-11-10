/**
 * Host check — PagerAxisOwnership + LibraryListOrder + ViewGroup disallow fixture.
 * Chay qua tools/host-check-miuix-phase.sh
 */
package io.chaldeaprjkt.gamespace.tools

import io.chaldeaprjkt.gamespace.preferences.LibraryListOrder
import io.chaldeaprjkt.gamespace.ui.gesture.PagerAxisOwnership
import io.chaldeaprjkt.gamespace.ui.gesture.PagerAxisOwnership.Axis
import io.chaldeaprjkt.gamespace.ui.gesture.PagerAxisOwnership.Event
import kotlin.system.exitProcess

object HostGestureOrderCheck {
    private val fails = ArrayList<String>()
    private fun check(cond: Boolean, msg: String) {
        if (!cond) fails.add(msg)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val cfg = PagerAxisOwnership.Config(touchSlopPx = 16f, dominantRatio = 1.0f)

        // --- Long vertical + X drift → van Vertical ---
        val verticalDrift = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 100f, 100f),
                Event.Move(0, 108f, 140f),
                Event.Move(0, 120f, 220f),
                Event.Move(0, 135f, 400f),
            ),
            cfg,
        )
        check(verticalDrift.axis == Axis.Vertical, "long vertical+X drift must stay Vertical")

        // --- Reverse upward ---
        val reverse = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 50f, 300f),
                Event.Move(0, 52f, 240f),
                Event.Move(0, 60f, 100f),
            ),
            cfg,
        )
        check(reverse.axis == Axis.Vertical, "upward scroll Vertical")

        // --- Horizontal intentional ---
        val horizontal = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 100f, 100f),
                Event.Move(0, 160f, 105f),
            ),
            cfg,
        )
        check(horizontal.axis == Axis.Horizontal, "intentional horizontal")

        // --- Tap within slop ---
        val tap = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 10f, 10f),
                Event.Move(0, 14f, 12f),
                Event.Up(0),
            ),
            cfg,
        )
        check(!tap.active && tap.axis == Axis.Undecided, "tap reset undecided")

        // --- Repeated down ---
        val second = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 0f, 0f),
                Event.Move(0, 0f, 50f),
                Event.Up(0),
                Event.Down(1, 0f, 0f),
                Event.Move(1, 0f, 80f),
            ),
            cfg,
        )
        check(second.active && second.axis == Axis.Vertical && second.pointerId == 1, "repeated down")

        // --- PointerUp khong successor → reset ---
        val ptrAlone = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 0f, 0f),
                Event.Move(0, 0f, 40f),
                Event.PointerUp(0),
            ),
            cfg,
        )
        check(!ptrAlone.active, "pointer up without successor resets")

        // --- f2: transfer primary + long vertical + X drift (giu Vertical) ---
        val transferDrift = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 100f, 100f),
                Event.Move(0, 105f, 150f), // lock Vertical tren pointer 0
                Event.PointerUp(0, successorId = 1, successorX = 110f, successorY = 160f),
                Event.Move(1, 130f, 280f), // X drift sau transfer
                Event.Move(1, 150f, 420f),
            ),
            cfg,
        )
        check(transferDrift.active, "transfer keeps active")
        check(transferDrift.pointerId == 1, "transfer primary → successor 1")
        check(transferDrift.axis == Axis.Vertical, "transfer preserves latched Vertical through X drift")

        // --- Transfer khi Undecided: rebase origin, roi lock vertical ---
        val transferUndecided = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 0f, 0f),
                Event.Move(0, 5f, 5f), // trong slop
                Event.PointerUp(0, successorId = 2, successorX = 50f, successorY = 50f),
                Event.Move(2, 55f, 100f), // dy=50 tu origin moi
            ),
            cfg,
        )
        check(
            transferUndecided.pointerId == 2 && transferUndecided.axis == Axis.Vertical,
            "undecided transfer rebases then locks vertical",
        )

        val canceled = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 0f, 0f),
                Event.Move(0, 0f, 40f),
                Event.Cancel,
            ),
            cfg,
        )
        check(!canceled.active, "cancel resets")

        // --- f3: ViewGroup DOWN clears disallow fixture ---
        check(
            !PagerAxisOwnership.viewGroupDownClearsDisallowFixture(
                setBeforeDown = true,
                restoreAfterDown = false,
            ),
            "DOWN clears pre-set disallow — MOVE sees false without restore",
        )
        check(
            PagerAxisOwnership.viewGroupDownClearsDisallowFixture(
                setBeforeDown = true,
                restoreAfterDown = true,
            ),
            "restore AFTER DOWN → MOVE sees disallow (vertical protect)",
        )
        // Horizontal path: after restore vertical, ownership Horizontal must release
        // (validated by wantsPagerDisallow)
        var s = PagerAxisOwnership.down(PagerAxisOwnership.reset(), 0, 0f, 0f)
        check(PagerAxisOwnership.wantsPagerDisallow(s), "undecided wants disallow")
        s = PagerAxisOwnership.move(s, 0, 80f, 2f, cfg)
        check(s.axis == Axis.Horizontal && !PagerAxisOwnership.wantsPagerDisallow(s), "horizontal releases disallow want")

        // --- Slider ownership: DOWN claim + horizontal drag → pager stays locked ---
        val sliderOwned = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 100f, 200f, childHorizontalClaim = true),
                Event.Move(0, 180f, 202f), // intentional horizontal on slider
                Event.Move(0, 40f, 198f), // reverse direction
                Event.Move(0, 220f, 205f), // repeated drag same sequence
            ),
            cfg,
        )
        check(sliderOwned.active, "slider sequence stays active")
        check(sliderOwned.axis == Axis.Horizontal, "slider drag classifies Horizontal")
        check(sliderOwned.childHorizontalClaim, "slider keeps childHorizontalClaim")
        check(
            PagerAxisOwnership.wantsPagerDisallow(sliderOwned),
            "slider ownership keeps pager disallow through horizontal",
        )
        check(
            !PagerAxisOwnership.mayReleasePagerDisallowPostMove(sliderOwned),
            "slider claim blocks post-MOVE pager release",
        )
        check(
            !PagerAxisOwnership.mayReleasePagerDisallowPreMove(sliderOwned, false),
            "slider: pre-MOVE unarmed still locked",
        )
        check(
            !PagerAxisOwnership.mayReleasePagerDisallowPreMove(sliderOwned, true),
            "slider claim blocks pre-MOVE release even if armed",
        )

        // --- Slider cancel clears claim ---
        val sliderCancel = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 0f, 0f, childHorizontalClaim = true),
                Event.Move(0, 50f, 1f),
                Event.Cancel,
            ),
            cfg,
        )
        check(!sliderCancel.active && !sliderCancel.childHorizontalClaim, "cancel clears slider claim")

        // --- Pointer handoff preserves slider claim ---
        val sliderHandoff = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 10f, 10f, childHorizontalClaim = true),
                Event.Move(0, 60f, 12f),
                Event.PointerUp(0, successorId = 1, successorX = 60f, successorY = 12f),
                Event.Move(1, 120f, 14f),
            ),
            cfg,
        )
        check(sliderHandoff.pointerId == 1, "slider handoff primary → 1")
        check(sliderHandoff.childHorizontalClaim, "slider claim survives pointer handoff")
        check(
            PagerAxisOwnership.wantsPagerDisallow(sliderHandoff),
            "handoff still blocks pager steal",
        )

        // --- Claim mid-sequence (Slider requestDisallow on MOVE in vertical RV) ---
        val midClaim = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 0f, 0f, childHorizontalClaim = false),
                Event.Move(0, 40f, 2f), // Horizontal classified
                Event.ClaimChildHorizontal, // child requestDisallow during super
            ),
            cfg,
        )
        check(midClaim.axis == Axis.Horizontal, "mid-claim keeps Horizontal axis")
        check(midClaim.childHorizontalClaim, "ClaimChildHorizontal latches")
        check(PagerAxisOwnership.wantsPagerDisallow(midClaim), "mid-claim restores pager protect")

        // --- Outside slider: horizontal still releases for tab swipe ---
        val outsideSlider = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 100f, 100f, childHorizontalClaim = false),
                Event.Move(0, 160f, 105f),
            ),
            cfg,
        )
        check(outsideSlider.axis == Axis.Horizontal, "outside slider Horizontal")
        check(!outsideSlider.childHorizontalClaim, "outside has no claim")
        check(
            !PagerAxisOwnership.wantsPagerDisallow(outsideSlider),
            "outside slider releases pager disallow want",
        )
        check(
            PagerAxisOwnership.mayReleasePagerDisallowPostMove(outsideSlider),
            "outside slider may release post-MOVE",
        )
        check(
            !PagerAxisOwnership.mayReleasePagerDisallowPreMove(outsideSlider, false),
            "outside: first Horizontal PRE deferred (unarmed)",
        )
        check(
            PagerAxisOwnership.mayReleasePagerDisallowPreMove(outsideSlider, true),
            "outside: after POST arm, PRE releases for tab swipe",
        )

        // --- One-frame defer gate: outside tab swipe ---
        var armed = false
        var gate = PagerAxisOwnership.disallowGateStep(outsideSlider, armed, preDispatch = true)
        check(gate.first && !gate.second, "outside frame1 PRE: disallow, not armed")
        gate = PagerAxisOwnership.disallowGateStep(outsideSlider, gate.second, preDispatch = false)
        check(!gate.first && gate.second, "outside frame1 POST: release + arm")
        armed = gate.second
        gate = PagerAxisOwnership.disallowGateStep(outsideSlider, armed, preDispatch = true)
        check(!gate.first, "outside frame2 PRE: released so pager can intercept")

        // --- Gate + slider claim: never arm/release ---
        gate = PagerAxisOwnership.disallowGateStep(sliderOwned, false, preDispatch = true)
        check(gate.first && !gate.second, "slider PRE locked")
        gate = PagerAxisOwnership.disallowGateStep(sliderOwned, gate.second, preDispatch = false)
        check(gate.first && !gate.second, "slider POST still locked, not armed")

        // --- Fixture: PRE-release steals at intercept; deferred keeps lock ---
        check(
            !PagerAxisOwnership.sliderClaimVsPreReleaseFixture(
                releaseBeforeDispatch = true,
                childClaimsDuringDispatch = true,
            ),
            "legacy PRE-release: intercept sees disallow=false (steal before child claim)",
        )
        check(
            PagerAxisOwnership.sliderClaimVsPreReleaseFixture(
                releaseBeforeDispatch = false,
                childClaimsDuringDispatch = true,
            ),
            "deferred PRE: intercept still sees disallow (slider can claim)",
        )
        check(
            PagerAxisOwnership.sliderClaimVsPreReleaseFixture(
                releaseBeforeDispatch = false,
                childClaimsDuringDispatch = false,
            ),
            "deferred PRE outside slider: intercept still locked this frame (release POST)",
        )

        // --- Miuix §4A.3: confirmHorizontalTakeoverClearTension fixture ---
        val preHorizontal = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 100f, 100f, childHorizontalClaim = false),
            ),
            cfg,
        )
        val postHorizontal = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 100f, 100f, childHorizontalClaim = false),
                Event.Move(0, 180f, 102f),
            ),
            cfg,
        )
        check(
            PagerAxisOwnership.shouldClearTensionOnHorizontalTakeover(preHorizontal, postHorizontal),
            "Horizontal takeover clears vertical tension",
        )
        val postHorizontalSlider = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 100f, 100f, childHorizontalClaim = true),
                Event.Move(0, 180f, 102f),
            ),
            cfg,
        )
        check(
            !PagerAxisOwnership.shouldClearTensionOnHorizontalTakeover(preHorizontal, postHorizontalSlider),
            "Slider claim does not trigger horizontal takeover clear tension",
        )

        // --- Miuix / LSPosed Horizontal Takeover (kc2AngleCommit) ---
        val startVerticalThenTakeover = PagerAxisOwnership.reduce(
            listOf(
                Event.Down(0, 100f, 100f, childHorizontalClaim = false),
                Event.Move(0, 105f, 125f), // dx=5, dy=25 -> Vertical ban dau
                Event.Move(0, 180f, 135f), // dx=80, dy=35 -> dx >= 1.5 * dy -> Horizontal Takeover!
            ),
            cfg,
        )
        check(
            startVerticalThenTakeover.axis == Axis.Horizontal,
            "horizontal takeover: intentional horizontal swipe overrides initial vertical drift",
        )

        // --- Library order ---
        val emptySnap = LibraryListOrder.snapshot(emptyList())
        check(
            LibraryListOrder.keysOf(emptySnap) == listOf(LibraryListOrder.KEY_ADD, LibraryListOrder.KEY_EMPTY),
            "empty: add then empty",
        )
        val labels = mapOf("b.pkg" to "Zebra", "a.pkg" to "Apple")
        val sorted = LibraryListOrder.sortGameKeys(listOf("b.pkg", "a.pkg")) { labels.getValue(it) }
        val filled = LibraryListOrder.snapshot(sorted)
        check(filled.first().key == LibraryListOrder.KEY_ADD, "add always first")
        check(
            LibraryListOrder.sameKeyOrder(
                LibraryListOrder.keysOf(filled),
                LibraryListOrder.keysOf(
                    LibraryListOrder.snapshot(
                        LibraryListOrder.sortGameKeys(listOf("a.pkg", "b.pkg")) { labels.getValue(it) },
                    ),
                ),
            ),
            "refresh invariance",
        )

        if (fails.isNotEmpty()) {
            println("FAIL HostGestureOrderCheck")
            fails.forEach { println(" - $it") }
            exitProcess(1)
        }
        println("PASS HostGestureOrderCheck (gesture+transfer+disallow+slider+order)")
        println("  transferDrift axis=${transferDrift.axis} ptr=${transferDrift.pointerId}")
        println("  downClears+restore=${PagerAxisOwnership.viewGroupDownClearsDisallowFixture(true, true)}")
        println(
            "  sliderOwned claim=${sliderOwned.childHorizontalClaim}" +
                " wantsDisallow=${PagerAxisOwnership.wantsPagerDisallow(sliderOwned)}" +
                " outsideRelease=${PagerAxisOwnership.mayReleasePagerDisallowPostMove(outsideSlider)}",
        )
    }
}
