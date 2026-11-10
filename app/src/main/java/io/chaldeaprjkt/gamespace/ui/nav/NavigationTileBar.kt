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
package io.chaldeaprjkt.gamespace.ui.nav

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

/**
 * Telegram floating bottom dock metrics (measured from screenshot):
 * - pill height ≈ 56.dp
 * - side inset ≈ 16.dp
 * - gap above gesture inset ≈ 8.dp
 */
internal val NavigationTileBarHeight = 56.dp
internal val NavigationTilePadding = 4.dp
internal val NavigationTileMinWidth = 64.dp

val NavigationTileBarHorizontalPadding = 16.dp

/** Gap between pill bottom and top of gesture-nav inset. */
val NavigationTileBarBottomGap = 8.dp

private const val SELECTED_INDICATOR_ALPHA = 0.15f

/** @deprecated Dung NavIndicatorGeometry.foldProgress — giu alias. */
internal fun foldSelectionProgressForDraw(progress: Float, tabCount: Int): Float =
    NavIndicatorGeometry.foldProgress(progress, tabCount)

/**
 * Real navigation-bar inset from the window root.
 * Compose WindowInsets are often 0 because EdgeToEdgeUtils already consumed them.
 */
@Composable
fun rememberRootNavigationBarBottom(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var insetPx by remember { mutableIntStateOf(0) }

    DisposableEffect(view) {
        val read = {
            insetPx = ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())
                ?.bottom
                ?: 0
        }
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> read() }
        val globalListener = ViewTreeObserver.OnGlobalLayoutListener { read() }
        view.addOnLayoutChangeListener(layoutListener)
        view.viewTreeObserver.addOnGlobalLayoutListener(globalListener)
        read()
        onDispose {
            view.removeOnLayoutChangeListener(layoutListener)
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(globalListener)
            }
        }
    }

    return with(density) { insetPx.toDp() }
}

/**
 * Telegram-style bottom dock:
 * - Soft **surface-colored** gradient from the gesture bar upward (not compositor
 *   BackgroundBlurDrawable — that path tints dark gray and is for cross-window blur).
 * - Scrim height stays within the pill+gesture stack so it does not spill above the bar.
 * - Floating pill sits above the gesture inset.
 */
@Composable
fun TelegramBottomNavDock(
    modifier: Modifier = Modifier,
    pill: @Composable () -> Unit,
) {
    val navBottom = rememberRootNavigationBarBottom()
    val bottomPadding = NavigationTileBarBottomGap + navBottom
    // Dock height = pill + gap + gesture only (no extra band above the pill).
    val dockHeight = bottomPadding + NavigationTileBarHeight
    // Scrim covers gesture → mid/lower pill area, matching Telegram ActionBarLayout
    // drawNavigationBarGradient (~1.33 × nav height, surfaceContainer tint).
    val scrimHeight = max(navBottom * 1.33f, navBottom + NavigationTileBarBottomGap + 12.dp)
    // Doi sang surfaceContainer theo chuan Expressive (khong dung surface legacy)
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dockHeight),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(scrimHeight)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to surfaceContainer.copy(alpha = 0.08f),
                            0.55f to surfaceContainer.copy(alpha = 0.55f),
                            0.88f to surfaceContainer.copy(alpha = 0.88f),
                            1.0f to surfaceContainer.copy(alpha = 0.96f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = NavigationTileBarHorizontalPadding,
                    end = NavigationTileBarHorizontalPadding,
                    bottom = bottomPadding,
                ),
            contentAlignment = Alignment.Center,
        ) {
            pill()
        }
    }
}

/**
 * Compact floating pill bar.
 *
 * [equalItemWidth]: cot deu = max(natural label widths, min touch), da bound available.
 * Indicator noi suy tren cot deu (RTL lat qua NavIndicatorGeometry) — khong chia rowWidth/tabCount
 * khi item natural width lech.
 */
@Composable
fun NavigationTileBar(
    tabCount: Int,
    selectionProgress: Float,
    equalItemWidth: Dp,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 3.dp,
    content: @Composable RowScope.() -> Unit,
) {
    require(tabCount > 0)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val columnWidthPx = with(density) { equalItemWidth.toPx() }
    val barWidth = equalItemWidth * tabCount + NavigationTilePadding * 2
    val folded = NavIndicatorGeometry.foldProgress(selectionProgress, tabCount)
    val frames = NavIndicatorGeometry.pillFrames(folded, tabCount, columnWidthPx, rtl = rtl)
    val pillColor = MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_INDICATOR_ALPHA)

    Surface(
        modifier = modifier
            .wrapContentWidth()
            .width(barWidth)
            .height(NavigationTileBarHeight)
            .heightIn(min = NavigationTileBarHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = shadowElevation,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(NavigationTilePadding)
                .clip(CircleShape),
        ) {
            // Indicator chi theo selectionProgress — khong spring owner rieng.
            frames.forEach { frame ->
                Box(
                    modifier = Modifier
                        .absoluteOffset { IntOffset(frame.xPx.roundToInt(), 0) }
                        .width(with(density) { frame.widthPx.toDp() })
                        .fillMaxHeight()
                        .background(color = pillColor, shape = CircleShape),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}
