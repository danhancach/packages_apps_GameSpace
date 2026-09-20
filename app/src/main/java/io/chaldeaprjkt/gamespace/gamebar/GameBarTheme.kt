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
package io.chaldeaprjkt.gamespace.gamebar

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Bo goc / mau dung chung GameBar — Material Expressive, khong hardcode #1A1A1A. */
object GameBarDimens {
    val PanelCorner = 28.dp
    val SectionCorner = 20.dp
    val TileCorner = 18.dp
    val ModeCorner = 16.dp
    val PanelWidth = 312.dp
    val TileIconSize = 52.dp
}

@Composable
fun gameBarPillContainer(): Color =
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)

@Composable
fun gameBarPillBorder(): Color =
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

@Composable
fun gameBarPillContent(): Color =
    MaterialTheme.colorScheme.onSurface

@Composable
fun GameMode.accentColor(): Color = when (this) {
    GameMode.Performance -> MaterialTheme.colorScheme.error
    GameMode.PowerSave -> MaterialTheme.colorScheme.tertiary
    GameMode.Balanced -> MaterialTheme.colorScheme.primary
}

@Composable
fun GameMode.accentContainer(): Color = when (this) {
    GameMode.Performance -> MaterialTheme.colorScheme.errorContainer
    GameMode.PowerSave -> MaterialTheme.colorScheme.tertiaryContainer
    GameMode.Balanced -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
fun GameMode.onAccentContainer(): Color = when (this) {
    GameMode.Performance -> MaterialTheme.colorScheme.onErrorContainer
    GameMode.PowerSave -> MaterialTheme.colorScheme.onTertiaryContainer
    GameMode.Balanced -> MaterialTheme.colorScheme.onPrimaryContainer
}

val GameBarPanelShape = RoundedCornerShape(GameBarDimens.PanelCorner)
val GameBarSectionShape = RoundedCornerShape(GameBarDimens.SectionCorner)
val GameBarTileShape = RoundedCornerShape(GameBarDimens.TileCorner)
val GameBarModeShape = RoundedCornerShape(GameBarDimens.ModeCorner)
