package io.chaldeaprjkt.gamespace.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Indication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.chaldeaprjkt.gamespace.R
import kotlin.math.roundToInt

private val NavigationTileItemSpacing = 1.dp
private val NavigationTileLabelHorizontalPadding = NavigationTilePadding * 2
private val NavigationTileIconSize = 22.dp

enum class SettingsNavItem(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    LIBRARY(R.string.tab_library, R.drawable.ic_nav_library),
    SETTINGS(R.string.tab_settings, R.drawable.ic_nav_settings),
}

val SettingsNavItems = SettingsNavItem.entries

@Composable
fun SettingsFloatingNavBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectionProgress: Float? = null,
) {
    val safeSelected = selectedIndex.coerceIn(0, SettingsNavItems.lastIndex)
    val targetProgress = selectionProgress ?: safeSelected.toFloat()
    val highlightIndex = pivotFloorMod(targetProgress.roundToInt(), SettingsNavItems.size)
    val hapticFeedback = LocalHapticFeedback.current
    // CircleShape clip on the tile keeps the ripple circular (AOSP M3 ripple API).
    val indication = remember { ripple(bounded = true) }

    NavigationTileBar(
        tabCount = SettingsNavItems.size,
        selectionProgress = targetProgress,
        modifier = modifier,
        shadowElevation = 3.dp,
    ) {
        SettingsNavItems.forEachIndexed { index, item ->
            SettingsNavBarItem(
                item = item,
                selected = highlightIndex == index,
                indication = indication,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onSelected(index)
                },
            )
        }
    }
}

@Composable
private fun RowScope.SettingsNavBarItem(
    item: SettingsNavItem,
    selected: Boolean,
    indication: Indication,
    onClick: () -> Unit,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .defaultMinSize(minWidth = NavigationTileMinWidth)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = indication,
                role = Role.Tab,
                onClick = { if (!selected) onClick() },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = NavigationTileItemSpacing,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        Icon(
            modifier = Modifier.size(NavigationTileIconSize),
            imageVector = ImageVector.vectorResource(item.iconRes),
            contentDescription = null,
            tint = contentColor,
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NavigationTileLabelHorizontalPadding),
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            color = contentColor,
        )
    }
}
