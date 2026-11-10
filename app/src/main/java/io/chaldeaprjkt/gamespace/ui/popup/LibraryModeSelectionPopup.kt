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

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.chaldeaprjkt.gamespace.R
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Popup chon mode Library — Miuix ListPopup channel (ch1→o70 / rg1 / l70).
 *
 * Motion: 1 owner (Animatable scale/alpha/scrim); open byte4 spring + byte5 tween200;
 * close byte7 spring + alpha rg1.c tween150 (dismiss gate); optional scrim 300/150.
 * Cite smali: /tmp/lsposed-smali/out/ch1.smali (Java ch1.o unsupported).
 * Lifecycle: ComponentDialog + Back dispatcher; dispose on detach.
 */
object LibraryModeSelectionPopup {
    data class ModeOption(val modeId: Int, val label: String)

    class Handle internal constructor(
        val dialog: Dialog,
        private val requestClose: () -> Unit,
        private val forceFlag: AtomicBoolean,
    ) {
        fun dismissAnimated() = requestClose()

        fun forceCleanup() {
            forceFlag.set(true)
            if (dialog.isShowing) dialog.dismiss()
        }
    }

    fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? Activity
    }

    fun show(
        anchor: android.view.View,
        modes: List<ModeOption>,
        selectedModeId: Int,
        removeLabel: String,
        onMode: (Int) -> Unit,
        onRemove: () -> Unit,
        onDismiss: () -> Unit,
    ): Handle? {
        val activity = findActivity(anchor.context) ?: return null

        val anchorScreen = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val anchorRect = LibraryPopupGeometry.OverlayRect(
            left = anchorScreen[0],
            top = anchorScreen[1],
            right = anchorScreen[0] + anchor.width,
            bottom = anchorScreen[1] + anchor.height,
        )

        val forceFlag = AtomicBoolean(false)
        val closeRequest = AtomicReference<(() -> Unit)?>(null)

        val dialog = ComponentDialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { onDismiss() }

        val compose = ComposeView(activity).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow,
            )
            setContent {
                LibraryPopupTheme {
                    SelectionPopupHost(
                        anchorScreen = anchorRect,
                        modes = modes,
                        selectedModeId = selectedModeId,
                        removeLabel = removeLabel,
                        forceFlag = forceFlag,
                        registerCloseRequest = { closeRequest.set(it) },
                        onMode = onMode,
                        onRemove = onRemove,
                        onDismissComplete = {
                            if (dialog.isShowing) dialog.dismiss()
                        },
                    )
                }
            }
        }

        dialog.setContentView(
            compose,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        dialog.window?.let { window ->
            applyTransparentSystemBars(window, activity)
        }
        dialog.onBackPressedDispatcher.addCallback(
            dialog,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    closeRequest.get()?.invoke()
                }
            },
        )

        dialog.show()
        // Theme translucent / show() co the ghi de flag — ap lai sau show.
        dialog.window?.let { window ->
            applyTransparentSystemBars(window, activity)
        }
        return Handle(
            dialog = dialog,
            requestClose = { closeRequest.get()?.invoke() },
            forceFlag = forceFlag,
        )
    }

    /**
     * Chi Dialog window — transparent status/nav; giu appearance icon tu Activity
     * (KHONG doan night). Goi truoc + sau show de song sot Theme_Translucent.
     */
    private fun applyTransparentSystemBars(window: Window, activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        window.setGravity(Gravity.TOP or Gravity.START)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val actWin = activity.window
        val fromActivity = WindowInsetsControllerCompat(actWin, actWin.decorView)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = fromActivity.isAppearanceLightStatusBars
            isAppearanceLightNavigationBars = fromActivity.isAppearanceLightNavigationBars
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryPopupTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) {
            androidx.compose.material3.darkColorScheme()
        } else {
            androidx.compose.material3.lightColorScheme()
        }
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
private fun SelectionPopupHost(
    anchorScreen: LibraryPopupGeometry.OverlayRect,
    modes: List<LibraryModeSelectionPopup.ModeOption>,
    selectedModeId: Int,
    removeLabel: String,
    forceFlag: AtomicBoolean,
    registerCloseRequest: (() -> Unit) -> Unit,
    onMode: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismissComplete: () -> Unit,
) {
    var closeState by remember { mutableStateOf(LibraryPopupGeometry.CloseState()) }
    var overlayOriginX by remember { mutableIntStateOf(0) }
    var overlayOriginY by remember { mutableIntStateOf(0) }
    var menuW by remember { mutableIntStateOf(0) }
    var menuH by remember { mutableIntStateOf(0) }
    var sizeReady by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var closingScaleVelocity by remember { mutableStateOf(0f) }

    // 1 transition owner: 3 Animatable channel (scale progress / alpha / scrim).
    val scaleProgress = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val scrimProgress = remember { Animatable(0f) }
    val scaleSpring = remember {
        spring<Float>(
            dampingRatio = LibraryPopupGeometry.ScaleSpringDamping,
            stiffness = LibraryPopupGeometry.ScaleSpringStiffness,
            visibilityThreshold = LibraryPopupGeometry.ScaleSpringThreshold,
        )
    }

    fun requestClose(action: (() -> Unit)? = null) {
        if (!closeState.acceptInput) return
        pendingAction = action
        // Capture before LaunchedEffect cancels the opening spring.
        closingScaleVelocity = scaleProgress.velocity
        closeState = LibraryPopupGeometry.reduceClose(
            closeState,
            LibraryPopupGeometry.CloseEvent.RequestClose,
        )
    }

    LaunchedEffect(Unit) {
        registerCloseRequest { requestClose() }
    }

    // Mo anim chi sau khi do xong size — tranh nhay layout tu width doan.
    LaunchedEffect(sizeReady) {
        if (sizeReady && !closeState.expanded && !closeState.closing) {
            closeState = LibraryPopupGeometry.reduceClose(
                closeState,
                LibraryPopupGeometry.CloseEvent.Opened,
            )
        }
    }

    // Channel motion — interrupt/velocity/overshoot qua Animatable.animateTo (spring).
    LaunchedEffect(closeState.expanded, closeState.closing, sizeReady) {
        if (forceFlag.get()) {
            onDismissComplete()
            return@LaunchedEffect
        }
        if (!sizeReady) return@LaunchedEffect
        when {
            closeState.expanded && !closeState.closing -> {
                // ch1 open: o70 byte4 scale spring; byte5 alpha tween200; byte6 scrim 300.
                coroutineScope {
                    launch {
                        scaleProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = scaleSpring,
                        )
                    }
                    launch {
                        contentAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(LibraryPopupGeometry.OpenAlphaDurationMs),
                        )
                    }
                    launch {
                        scrimProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                LibraryPopupGeometry.ScrimOpenDurationMs,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                }
            }
            closeState.closing -> {
                // ch1 close: byte7 scale spring to0; byte8 scrim 150; wait alpha rg1.c 150.
                coroutineScope {
                    val scaleClose = launch {
                        scaleProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = scaleSpring,
                            initialVelocity = closingScaleVelocity,
                        )
                    }
                    val scrimClose = launch {
                        scrimProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                LibraryPopupGeometry.ScrimCloseDurationMs,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                    contentAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(LibraryPopupGeometry.CloseAlphaDurationMs),
                    )
                    scrimClose.join()
                    // ch1 hides after alpha reaches zero; an invisible spring must
                    // not keep this full-window input surface alive until it settles.
                    scaleClose.cancel()
                }
                if (forceFlag.get()) {
                    onDismissComplete()
                    return@LaunchedEffect
                }
                closeState = LibraryPopupGeometry.reduceClose(
                    closeState,
                    LibraryPopupGeometry.CloseEvent.TransitionFinished,
                )
            }
        }
    }

    val hostView = LocalView.current
    DisposableEffect(hostView) {
        val loc = IntArray(2)
        val listener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            hostView.getLocationOnScreen(loc)
            overlayOriginX = loc[0]
            overlayOriginY = loc[1]
        }
        hostView.getLocationOnScreen(loc)
        overlayOriginX = loc[0]
        overlayOriginY = loc[1]
        hostView.addOnLayoutChangeListener(listener)
        onDispose { hostView.removeOnLayoutChangeListener(listener) }
    }

    LaunchedEffect(closeState.shouldDismiss) {
        if (closeState.shouldDismiss) {
            val action = pendingAction
            pendingAction = null
            onDismissComplete()
            action?.invoke()
        }
    }

    val density = LocalDensity.current
    val corner = dimensionResource(R.dimen.gamespace_dialog_corner_radius)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val layoutDirection = LocalLayoutDirection.current
    val accept = closeState.acceptInput

    // scale = p*0.85+0.15 — KHONG clamp p (overshoot spring).
    val scale = LibraryPopupGeometry.scaleFromProgress(scaleProgress.value)
    val alpha = contentAlpha.value
    val scrimA = scrimProgress.value

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val overlayW = constraints.maxWidth
        val overlayH = constraints.maxHeight
        val edgePx = with(density) { 8.dp.roundToPx() }
        val safeInsetsPx = run {
            val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            LibraryPopupGeometry.SafeInsets(
                left = safe.getLeft(density, layoutDirection),
                top = safe.getTop(density),
                right = safe.getRight(density, layoutDirection),
                bottom = safe.getBottom(density),
            )
        }
        val contentW = (overlayW - safeInsetsPx.left - safeInsetsPx.right).coerceAtLeast(0)
        val availableWidth = with(density) { (contentW - 2 * edgePx).coerceAtLeast(0).toDp() }
        val anchorLocal = LibraryPopupGeometry.anchorInOverlay(
            anchorScreen = anchorScreen,
            overlayOriginX = overlayOriginX,
            overlayOriginY = overlayOriginY,
        )
        // Truoc measure: dat 0,0 an; sau measure dung size that — khong doan MenuMaxDp.
        val measuredW = menuW
        val measuredH = menuH
        val placement = if (sizeReady && measuredW > 0 && measuredH > 0) {
            LibraryPopupGeometry.placeMenuInSafeBounds(
                overlayWidth = overlayW,
                overlayHeight = overlayH,
                insets = safeInsetsPx,
                anchorOverlay = anchorLocal,
                menuWidth = measuredW,
                menuHeight = measuredH,
                layoutRtl = rtl,
                edgePx = edgePx,
            )
        } else {
            LibraryPopupGeometry.Placement(
                x = safeInsetsPx.left,
                y = safeInsetsPx.top,
                pivotX = if (rtl) 0f else 1f,
                pivotY = 0f,
                placeAbove = false,
                maxHeightPx = (
                    overlayH - safeInsetsPx.top - safeInsetsPx.bottom - edgePx * 2
                    ).coerceAtLeast(0),
            )
        }
        val origin = TransformOrigin(placement.pivotX, placement.pivotY)
        val visibleAlpha = if (sizeReady) alpha else 0f
        val visibleScrim = if (sizeReady) scrimA else 0f

        // Scrim full window (khong inset).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(alpha = LibraryPopupGeometry.ScrimAlpha * visibleScrim),
                )
                .clickable(
                    enabled = accept && sizeReady,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { requestClose() },
                ),
        )
        // Bound the intrinsic content width to the safe window. Weighted labels
        // shrink inside that width while preserving the trailing check gutter.
        Surface(
            shape = RoundedCornerShape(corner),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(AbsoluteAlignment.TopLeft)
                .absoluteOffset { IntOffset(placement.x, placement.y) }
                .widthIn(
                    min = minOf(LibraryPopupGeometry.MenuMinDp.dp, availableWidth),
                    max = minOf(LibraryPopupGeometry.MenuMaxDp.dp, availableWidth),
                )
                .width(IntrinsicSize.Max)
                .heightIn(max = with(density) { placement.maxHeightPx.toDp() })
                .onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0) {
                        menuW = size.width
                        menuH = size.height
                        sizeReady = true
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = visibleAlpha
                    transformOrigin = origin
                },
        ) {
            // Scroll nam trong Surface da intrinsic — KHONG width(IntrinsicSize) tren scroll
            // (scroll maxHeight infinity pha intrinsic). fillMaxWidth = fill width da chot.
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                modes.forEach { opt ->
                    val selected = opt.modeId == selectedModeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = accept) {
                                requestClose { onMode(opt.modeId) }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            text = opt.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        // Gutter check moi hang — selection khong doi intrinsic width.
                        Box(
                            modifier = Modifier.size(LibraryPopupGeometry.CheckGutterDp.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(
                                        LibraryPopupGeometry.CheckGutterDp.dp,
                                    ),
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = accept) {
                            requestClose { onRemove() }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        text = removeLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
