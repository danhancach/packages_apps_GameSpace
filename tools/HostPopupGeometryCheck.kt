/**
 * Host check — LibraryPopupGeometry Miuix ListPopup + close + full-card hit + safe place.
 *
 * Motion: rg1.a spring 0.82/362.5/1e-4 + alpha 200/150 — KHONG video duration / xp1 0.6/800.
 */
package io.chaldeaprjkt.gamespace.tools

import io.chaldeaprjkt.gamespace.ui.popup.LibraryPopupGeometry
import io.chaldeaprjkt.gamespace.ui.popup.LibraryPopupGeometry.CloseEvent
import io.chaldeaprjkt.gamespace.ui.popup.LibraryPopupGeometry.CloseState
import io.chaldeaprjkt.gamespace.ui.popup.LibraryPopupGeometry.OverlayRect
import io.chaldeaprjkt.gamespace.ui.popup.LibraryPopupGeometry.SafeInsets
import kotlin.math.abs
import kotlin.system.exitProcess

object HostPopupGeometryCheck {
    private val fails = ArrayList<String>()
    private fun check(cond: Boolean, msg: String) {
        if (!cond) fails.add(msg)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // --- Miuix ListPopup constants (l70 / rg1.a/b/c/d/e) ---
        check(LibraryPopupGeometry.CollapsedScale == 0.15f, "l70 collapsed scale 0.15")
        check(LibraryPopupGeometry.ExpandedScale == 1.0f, "expanded scale 1.0")
        check(LibraryPopupGeometry.ScaleSpringDamping == 0.82f, "rg1.a damping 0.82")
        check(LibraryPopupGeometry.ScaleSpringStiffness == 362.5f, "rg1.a stiffness 362.5")
        check(LibraryPopupGeometry.ScaleSpringThreshold == 1.0e-4f, "rg1.a threshold 1e-4")
        check(LibraryPopupGeometry.OpenAlphaDurationMs == 200, "rg1.b open alpha 200ms")
        check(LibraryPopupGeometry.CloseAlphaDurationMs == 150, "rg1.c close alpha 150ms")
        check(LibraryPopupGeometry.ScrimOpenDurationMs == 300, "rg1.d scrim open 300ms")
        check(LibraryPopupGeometry.ScrimCloseDurationMs == 150, "rg1.e scrim close 150ms")
        check(LibraryPopupGeometry.EaseX1 == 0.4f && LibraryPopupGeometry.EaseX2 == 0.2f, "se0.a bezier")
        check(abs(LibraryPopupGeometry.scaleFromProgress(0f) - 0.15f) < 1e-5f, "scale@0 = 0.15")
        check(abs(LibraryPopupGeometry.scaleFromProgress(1f) - 1.0f) < 1e-5f, "scale@1 = 1.0")
        check(abs(LibraryPopupGeometry.scaleFromProgress(0.5f) - 0.575f) < 1e-5f, "scale@0.5 mid")
        // Overshoot: KHONG clamp progress tai 1
        check(
            abs(LibraryPopupGeometry.scaleFromProgress(1.1f) - (1.1f * 0.85f + 0.15f)) < 1e-5f,
            "scale overshoot p=1.1 unclamped",
        )
        check(LibraryPopupGeometry.scaleFromProgress(1.1f) > 1.0f, "overshoot scale > 1")
        // Guard: khong quay ve Material xp1 / video close 100
        check(LibraryPopupGeometry.CollapsedScale != 0.8f, "NOT Material collapsed 0.8")
        check(LibraryPopupGeometry.ScaleSpringDamping != 0.6f, "NOT Material damping 0.6")
        check(LibraryPopupGeometry.ScaleSpringStiffness != 800f, "NOT Material stiffness 800")
        check(LibraryPopupGeometry.CloseAlphaDurationMs != 100, "NOT video close 100ms")

        check(LibraryPopupGeometry.ScrimAlpha == 0.32f, "scrim alpha observe")
        check(LibraryPopupGeometry.MenuMinDp == 112, "menu min 112dp")
        check(LibraryPopupGeometry.MenuMaxDp == 280, "menu max 280dp (cap, not forced width)")
        check(LibraryPopupGeometry.CheckGutterDp == 20, "check gutter reserved 20dp")

        val anchorScreen = OverlayRect(100, 200, 180, 260)
        val local = LibraryPopupGeometry.anchorInOverlay(anchorScreen, 40, 50)
        check(local == OverlayRect(60, 150, 140, 210), "anchorInOverlay subtract origin")

        val below = LibraryPopupGeometry.placeMenu(
            overlayWidth = 1080,
            overlayHeight = 2520,
            anchor = OverlayRect(800, 400, 1000, 480),
            menuWidth = 280,
            menuHeight = 320,
            layoutRtl = false,
        )
        check(!below.placeAbove, "fits below → placeBelow")
        check(below.y == 480, "below y = anchor.bottom")
        check(below.x == 1000 - 280, "LTR align end")
        check(below.pivotX == 1f && below.pivotY == 0f, "LTR below pivot top-end")

        val nearBottom = LibraryPopupGeometry.placeMenu(
            overlayWidth = 1080,
            overlayHeight = 800,
            anchor = OverlayRect(700, 600, 900, 680),
            menuWidth = 280,
            menuHeight = 320,
            layoutRtl = false,
        )
        check(nearBottom.placeAbove, "bottom clip → placeAbove")
        check(nearBottom.pivotY == 1f, "above pivot bottom-end")

        val narrow = LibraryPopupGeometry.placeMenu(
            overlayWidth = 200,
            overlayHeight = 800,
            anchor = OverlayRect(150, 100, 190, 160),
            menuWidth = 280,
            menuHeight = 200,
            layoutRtl = false,
            edgePx = 8,
        )
        check(narrow.x == 8, "narrow clamp to edgePx")

        val rtl = LibraryPopupGeometry.placeMenu(
            overlayWidth = 1080,
            overlayHeight = 2520,
            anchor = OverlayRect(80, 400, 200, 480),
            menuWidth = 280,
            menuHeight = 300,
            layoutRtl = true,
        )
        check(rtl.x == 80, "RTL x = anchor.left")
        check(rtl.pivotX == 0f && rtl.pivotY == 0f, "RTL below pivot top-start")

        val tall = LibraryPopupGeometry.placeMenu(
            overlayWidth = 1080,
            overlayHeight = 600,
            anchor = OverlayRect(400, 100, 500, 160),
            menuWidth = 200,
            menuHeight = 2000,
            layoutRtl = false,
            edgePx = 8,
        )
        check(tall.maxHeightPx == 600 - 16, "large-content maxHeight")

        // Safe placement: status/nav/cutout inset; menu offset +inset; maxH shrinks.
        val insets = SafeInsets(left = 0, top = 80, right = 0, bottom = 70)
        val safePlace = LibraryPopupGeometry.placeMenuInSafeBounds(
            overlayWidth = 1080,
            overlayHeight = 2520,
            insets = insets,
            anchorOverlay = OverlayRect(800, 400, 1000, 480),
            menuWidth = 280,
            menuHeight = 320,
            layoutRtl = false,
        )
        check(safePlace.y == 480, "safe below y still anchor.bottom (within content)")
        check(safePlace.x == 1000 - 280, "safe LTR x")
        check(
            safePlace.maxHeightPx == (2520 - 80 - 70) - 16,
            "safe maxHeight excludes status+nav",
        )

        var s = CloseState()
        s = LibraryPopupGeometry.reduceClose(s, CloseEvent.Opened)
        check(s.expanded && s.acceptInput && !s.closing, "opened accepts input")
        s = LibraryPopupGeometry.reduceClose(s, CloseEvent.RequestClose)
        check(!s.expanded && s.closing && !s.shouldDismiss && !s.acceptInput, "request → closing")
        val gated = LibraryPopupGeometry.reduceClose(s, CloseEvent.RequestClose)
        check(gated.closing && !gated.acceptInput, "repeat request gated")
        s = LibraryPopupGeometry.reduceClose(s, CloseEvent.TransitionFinished)
        check(s.shouldDismiss, "transition finished → dismiss")
        val force = LibraryPopupGeometry.reduceClose(CloseState(expanded = true), CloseEvent.ForceCleanup)
        check(force.shouldDismiss && !force.expanded, "forceCleanup")

        if (fails.isNotEmpty()) {
            println("FAIL HostPopupGeometryCheck")
            fails.forEach { println(" - $it") }
            exitProcess(1)
        }
        println("PASS HostPopupGeometryCheck (Miuix ch1/o70/rg1.a spring + safe placement + close contract)")
        println("  scale spring 0.82/362.5; alpha 200/150; no clamp@1; no video 100ms")
    }
}
