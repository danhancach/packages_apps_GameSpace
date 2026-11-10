/*
 * Copyright (C) 2026 danhancach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.ui.popup

/**
 * Geometry / motion / close-contract — Miuix ListPopup (KHONG Material xp1 DropDownMenu).
 *
 * Motion SSOT (channel owner = Animatable per channel, 1 host):
 * - ch1.smali open: o70 byte4 scale → rg1.a spring(0.82, 362.5, 1e-4);
 *   o70 byte5 alpha → rg1.b tween200; optional o70 byte6 scrim → rg1.d tween300.
 * - ch1.smali close: o70 byte7 scale → rg1.a to0; optional byte8 scrim → rg1.e tween150;
 *   wait ch1.q alpha → rg1.c tween150 roi hide (Java ch1.o UnsupportedOperationException).
 * - Cite: /tmp/lsposed-smali/out/ch1.smali ~1260–1391; o70.smali cases 4/5/6/7/8;
 *   rg1.java a/b/c/d/e; l70 scale = p*0.85+0.15 (KHONG clamp p tai 1).
 * - Trace: .logs/gamespace-popup-fidelity-review-20261109/miuix-motion-trace.md
 *
 * KHONG video duration / Material xp1 0.6/800 / tween chung scale+alpha.
 */
object LibraryPopupGeometry {
    /** Quan sat scrim toi (runtime); alpha cu the khong do chinh xac. */
    const val ScrimAlpha = 0.32f

    /** l70: progress 0 → scale 0.15 (co the overshoot khi spring >1). */
    const val CollapsedScale = 0.15f
    const val ExpandedScale = 1.0f
    const val ScaleProgressFactor = 0.85f

    /** rg1.a / rg1.f — SpringSpec dampingRatio / stiffness / visibilityThreshold. */
    const val ScaleSpringDamping = 0.82f
    const val ScaleSpringStiffness = 362.5f
    const val ScaleSpringThreshold = 1.0e-4f

    /** rg1.b — open alpha tween ms. */
    const val OpenAlphaDurationMs = 200
    /** rg1.c — close alpha tween ms (dismiss gate). */
    const val CloseAlphaDurationMs = 150
    /** rg1.d — optional scrim open. */
    const val ScrimOpenDurationMs = 300
    /** rg1.e — optional scrim close. */
    const val ScrimCloseDurationMs = 150

    /**
     * se0.a CubicBezier(0.4, 0, 0.2, 1) — FastOutSlowIn-equivalent (scrim / legacy ease).
     * Host-testable; KHONG dung lam scale (scale = spring).
     */
    const val EaseX1 = 0.4f
    const val EaseY1 = 0.0f
    const val EaseX2 = 0.2f
    const val EaseY2 = 1.0f

    /**
     * Menu width: intrinsic (label dai nhat + check gutter + pad 16*2), roi widthIn cap.
     * MenuMaxDp = tran an toan (KHONG force width = max); MenuMinDp san ngan.
     */
    const val MenuMinDp = 112
    const val MenuMaxDp = 280
    /** Cho check icon moi option — selection khong doi width. */
    const val CheckGutterDp = 20
    const val EdgeMarginPx = 8

    /**
     * l70 scale mapping — KHONG coerce progress (giu overshoot/velocity spring).
     * p=0 → 0.15; p=1 → 1.0; p>1 → >1.
     */
    fun scaleFromProgress(progress: Float): Float =
        progress * ScaleProgressFactor + CollapsedScale

    data class OverlayRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = (right - left).coerceAtLeast(0)
        val height: Int get() = (bottom - top).coerceAtLeast(0)
    }

    data class Placement(
        val x: Int,
        val y: Int,
        val pivotX: Float,
        val pivotY: Float,
        val placeAbove: Boolean,
        val maxHeightPx: Int,
    )

    data class SafeInsets(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    fun anchorInOverlay(
        anchorScreen: OverlayRect,
        overlayOriginX: Int,
        overlayOriginY: Int,
    ): OverlayRect = OverlayRect(
        left = anchorScreen.left - overlayOriginX,
        top = anchorScreen.top - overlayOriginY,
        right = anchorScreen.right - overlayOriginX,
        bottom = anchorScreen.bottom - overlayOriginY,
    )

    /** Dich anchor tu toa do window → content (tru status/nav/cutout). */
    fun anchorInSafeContent(
        anchorOverlay: OverlayRect,
        insets: SafeInsets,
    ): OverlayRect = OverlayRect(
        left = anchorOverlay.left - insets.left,
        top = anchorOverlay.top - insets.top,
        right = anchorOverlay.right - insets.left,
        bottom = anchorOverlay.bottom - insets.top,
    )

    /**
     * pi2 PopupLayoutPosition trong safe content (exclude systemBars+cutout).
     * Scrim van full window — chi placement dung bounds nay.
     */
    fun placeMenuInSafeBounds(
        overlayWidth: Int,
        overlayHeight: Int,
        insets: SafeInsets,
        anchorOverlay: OverlayRect,
        menuWidth: Int,
        menuHeight: Int,
        layoutRtl: Boolean,
        gapPx: Int = 0,
        edgePx: Int = EdgeMarginPx,
    ): Placement {
        val contentW = (overlayWidth - insets.left - insets.right).coerceAtLeast(0)
        val contentH = (overlayHeight - insets.top - insets.bottom).coerceAtLeast(0)
        val local = placeMenu(
            overlayWidth = contentW,
            overlayHeight = contentH,
            anchor = anchorInSafeContent(anchorOverlay, insets),
            menuWidth = menuWidth,
            menuHeight = menuHeight,
            layoutRtl = layoutRtl,
            gapPx = gapPx,
            edgePx = edgePx,
        )
        return local.copy(
            x = local.x + insets.left,
            y = local.y + insets.top,
        )
    }

    /**
     * pi2 PopupLayoutPosition: uu tien below; clip → above; isRightAligned (!RTL).
     * Pivot theo goc neo (ListPopupLayoutInfo.effectiveTransformOrigin).
     */
    fun placeMenu(
        overlayWidth: Int,
        overlayHeight: Int,
        anchor: OverlayRect,
        menuWidth: Int,
        menuHeight: Int,
        layoutRtl: Boolean,
        gapPx: Int = 0,
        edgePx: Int = EdgeMarginPx,
    ): Placement {
        val safeOverlayW = overlayWidth.coerceAtLeast(0)
        val safeOverlayH = overlayHeight.coerceAtLeast(0)
        val w = menuWidth.coerceIn(0, (safeOverlayW - edgePx * 2).coerceAtLeast(0))
        val maxH = (safeOverlayH - edgePx * 2).coerceAtLeast(0)
        val h = menuHeight.coerceIn(0, maxH)

        val belowY = anchor.bottom + gapPx
        val aboveY = anchor.top - h - gapPx
        val placeAbove = belowY + h > safeOverlayH - edgePx && aboveY >= edgePx
        val rawY = if (placeAbove) aboveY else belowY
        val y = rawY.coerceIn(edgePx, (safeOverlayH - h - edgePx).coerceAtLeast(edgePx))

        val rawX = if (layoutRtl) {
            anchor.left
        } else {
            anchor.right - w
        }
        val x = rawX.coerceIn(edgePx, (safeOverlayW - w - edgePx).coerceAtLeast(edgePx))

        val pivotX = if (layoutRtl) 0f else 1f
        val pivotY = if (placeAbove) 1f else 0f
        return Placement(
            x = x,
            y = y,
            pivotX = pivotX,
            pivotY = pivotY,
            placeAbove = placeAbove,
            maxHeightPx = maxH,
        )
    }

    data class CloseState(
        val expanded: Boolean = false,
        val closing: Boolean = false,
        val shouldDismiss: Boolean = false,
        val acceptInput: Boolean = true,
    )

    sealed class CloseEvent {
        data object Opened : CloseEvent()
        data object RequestClose : CloseEvent()
        data object TransitionFinished : CloseEvent()
        data object ForceCleanup : CloseEvent()
    }

    fun reduceClose(state: CloseState, event: CloseEvent): CloseState = when (event) {
        CloseEvent.Opened -> state.copy(
            expanded = true,
            closing = false,
            shouldDismiss = false,
            acceptInput = true,
        )
        CloseEvent.RequestClose -> {
            if (state.closing || state.shouldDismiss) {
                state.copy(acceptInput = false)
            } else if (!state.expanded) {
                state.copy(shouldDismiss = true, acceptInput = false, closing = true)
            } else {
                state.copy(
                    expanded = false,
                    closing = true,
                    acceptInput = false,
                    shouldDismiss = false,
                )
            }
        }
        CloseEvent.TransitionFinished -> {
            if (state.closing) {
                state.copy(shouldDismiss = true, acceptInput = false)
            } else {
                state
            }
        }
        CloseEvent.ForceCleanup -> state.copy(
            expanded = false,
            closing = true,
            shouldDismiss = true,
            acceptInput = false,
        )
    }
}
