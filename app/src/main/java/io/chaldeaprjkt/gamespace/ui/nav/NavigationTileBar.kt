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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.floor
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

internal fun foldSelectionProgressForDraw(progress: Float, tabCount: Int): Float {
    val count = tabCount.coerceAtLeast(1).toFloat()
    if (!progress.isFinite()) return 0f
    val min = -1f
    val max = count + 1f
    var folded = progress
    val span = count
    if (folded >= max) {
        folded -= floor((folded - min) / span) * span
    }
    if (folded < min) {
        folded += floor((max - folded - 0.0001f) / span) * span
    }
    while (folded >= max) folded -= span
    while (folded < min) folded += span
    return folded
}

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
    // drawNavigationBarGradient (~1.33 × nav height, surface tint).
    val scrimHeight = max(navBottom * 1.33f, navBottom + NavigationTileBarBottomGap + 12.dp)
    val surface = MaterialTheme.colorScheme.surface

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
                            0.0f to surface.copy(alpha = 0.08f),
                            0.55f to surface.copy(alpha = 0.55f),
                            0.88f to surface.copy(alpha = 0.88f),
                            1.0f to surface.copy(alpha = 0.96f),
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

@Composable
fun NavigationTileBar(
    tabCount: Int,
    selectionProgress: Float,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 3.dp,
    content: @Composable RowScope.() -> Unit,
) {
    require(tabCount > 0)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(NavigationTileBarHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = shadowElevation,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(NavigationTilePadding)
                .clip(CircleShape),
        ) {
            val itemWidth = maxWidth / tabCount
            val count = tabCount.toFloat()
            val folded = foldSelectionProgressForDraw(selectionProgress, tabCount)

            @Composable
            fun IndicatorPill(progress: Float) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (itemWidth * progress).roundToPx(),
                                y = 0,
                            )
                        }
                        .width(itemWidth)
                        .fillMaxHeight()
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(
                                alpha = SELECTED_INDICATOR_ALPHA,
                            ),
                            shape = CircleShape,
                        ),
                )
            }

            IndicatorPill(folded)
            when {
                folded > count - 1f -> IndicatorPill(folded - count)
                folded < 0f -> IndicatorPill(folded + count)
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}
