package io.chaldeaprjkt.gamespace.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.chaldeaprjkt.gamespace.R
import kotlin.math.roundToInt

private val NavigationTileItemSpacing = 1.dp
private val NavigationTileLabelHorizontalPadding = NavigationTilePadding * 2
private val NavigationTileIconSize = 22.dp
private val NavigationTileTouchMin = 48.dp

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
    isDragging: Boolean = false,
) {
    val safeSelected = selectedIndex.coerceIn(0, SettingsNavItems.lastIndex)
    val rawProgress = selectionProgress ?: safeSelected.toFloat()
    // Khi dang vuot tay (isDragging): bam 1:1 theo ngon tay; khi idle/settle (click tab): chay spring Miuix/M3
    val targetProgress = if (isDragging) {
        rawProgress
    } else {
        animateFloatAsState(
            targetValue = rawProgress,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f), // Miuix §4C & §2A defaultSpatial
            label = "tab_indicator_spring",
        ).value
    }
    val highlightIndex = rawProgress.roundToInt().coerceIn(0, SettingsNavItems.lastIndex)
    val hapticFeedback = LocalHapticFeedback.current
    val indication = remember { ripple(bounded = true) }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labels = SettingsNavItems.map { stringResource(it.labelRes) }
    // Do geometry theo fontScale / nhan da ngon ngu (narrow vs large).
    val naturalWidthsPx = remember(labels, labelStyle, density, textMeasurer) {
        val padPx = with(density) { NavigationTileLabelHorizontalPadding.toPx() * 2 }
        val iconBand = with(density) { NavigationTileIconSize.toPx() }
        labels.map { label ->
            val textW = textMeasurer.measure(
                text = AnnotatedString(label),
                style = labelStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width.toFloat()
            maxOf(textW + padPx, iconBand + padPx)
        }.toFloatArray()
    }

    BoxWithConstraints(modifier = modifier.wrapContentWidth()) {
        val minTouchPx = with(density) { NavigationTileTouchMin.toPx() }
        val minWidthPx = with(density) { NavigationTileMinWidth.toPx() }
        val floorPx = maxOf(minTouchPx, minWidthPx)
        // available: dock pad ngang da tru o TelegramBottomNavDock; day la max hop ly local.
        val availablePx = with(density) {
            (maxWidth - NavigationTilePadding * 2).coerceAtLeast(0.dp).toPx()
        }
        val columnPx = NavIndicatorGeometry.equalColumnWidthPx(
            naturalWidthsPx = naturalWidthsPx,
            minTouchPx = floorPx,
            availableWidthPx = availablePx,
        )
        val equalItemWidth = with(density) { columnPx.toDp() }

        NavigationTileBar(
            tabCount = SettingsNavItems.size,
            selectionProgress = targetProgress,
            equalItemWidth = equalItemWidth,
            shadowElevation = 3.dp,
        ) {
            SettingsNavItems.forEachIndexed { index, item ->
                SettingsNavBarItem(
                    item = item,
                    selected = highlightIndex == index,
                    indication = indication,
                    equalWidth = equalItemWidth,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onSelected(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun RowScope.SettingsNavBarItem(
    item: SettingsNavItem,
    selected: Boolean,
    indication: Indication,
    equalWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val contentAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.75f,
        animationSpec = spring(dampingRatio = 1.0f, stiffness = 1600f), // Miuix §2A defaultEffects
        label = "nav_item_alpha",
    )
    val targetColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val contentColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = spring(dampingRatio = 1.0f, stiffness = 1600f), // Miuix §2A defaultEffects
        label = "nav_item_color",
    )

    Column(
        modifier = Modifier
            .width(equalWidth)
            .fillMaxHeight()
            .defaultMinSize(minHeight = NavigationTileTouchMin)
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
            tint = contentColor.copy(alpha = contentAlpha),
        )
        Text(
            modifier = Modifier.padding(horizontal = NavigationTileLabelHorizontalPadding),
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            color = contentColor.copy(alpha = contentAlpha),
        )
    }
}
