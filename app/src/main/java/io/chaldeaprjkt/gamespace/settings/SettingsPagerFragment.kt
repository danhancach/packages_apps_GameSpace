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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.chaldeaprjkt.gamespace.settings

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.ui.nav.ContinuousSelectionProgressHolder
import io.chaldeaprjkt.gamespace.ui.nav.SettingsFloatingNavBar
import io.chaldeaprjkt.gamespace.ui.nav.SettingsNavItems
import io.chaldeaprjkt.gamespace.ui.nav.TelegramBottomNavDock
import io.chaldeaprjkt.gamespace.ui.nav.nearestLoopPage
import io.chaldeaprjkt.gamespace.ui.nav.pivotFloorMod
import io.chaldeaprjkt.gamespace.ui.nav.updateFromAbsolutePage
import kotlin.math.abs

/**
 * Looping ViewPager2 + Compose floating pill bar (DataBackup / SukiSU metrics).
 *
 * The bar is reparented onto [android.R.id.content] so it stays pinned above the
 * collapsing toolbar content (not clipped / scrolled away with [R.id.content_frame]).
 */
class SettingsPagerFragment : Fragment() {

    private val progressHolder = ContinuousSelectionProgressHolder()
    private var selectionProgress by mutableFloatStateOf(0f)
    private var selectedLogical by mutableIntStateOf(0)
    private var overlayNav: ComposeView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.settings_pager, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pager = view.findViewById<ViewPager2>(R.id.settings_pager)
        val composeNav = view.findViewById<ComposeView>(R.id.settings_nav_compose)

        val pageCount = SettingsNavItems.size
        val loopTotal = pageCount * LOOP_MULTIPLIER
        val loopStart = (loopTotal / 2).let { mid -> mid - mid % pageCount }

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = loopTotal

            override fun createFragment(position: Int): Fragment =
                when (pivotFloorMod(position, pageCount)) {
                    0 -> LibraryFragment()
                    else -> SettingsFragment()
                }
        }
        pager.offscreenPageLimit = 1
        // Prefer horizontal paging only — vertical nested scroll stays on preference lists.
        pager.isUserInputEnabled = true
        pager.setCurrentItem(loopStart, false)
        selectedLogical = 0
        selectionProgress = 0f
        progressHolder.update(0f, settled = true, pageCount = pageCount)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int,
            ) {
                val settling = abs(positionOffset) < 0.001f &&
                    pager.scrollState == ViewPager2.SCROLL_STATE_IDLE
                selectionProgress = progressHolder.updateFromAbsolutePage(
                    absolutePage = position,
                    pageOffset = positionOffset,
                    settled = settling,
                    logicalPageCount = pageCount,
                )
                selectedLogical = pivotFloorMod(
                    if (positionOffset >= 0.5f) position + 1 else position,
                    pageCount,
                )
            }

            override fun onPageSelected(position: Int) {
                selectedLogical = pivotFloorMod(position, pageCount)
                updateActivityTitle(selectedLogical)
                if (pager.scrollState == ViewPager2.SCROLL_STATE_IDLE) {
                    selectionProgress = progressHolder.updateFromAbsolutePage(
                        absolutePage = position,
                        pageOffset = 0f,
                        settled = true,
                        logicalPageCount = pageCount,
                    )
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val position = pager.currentItem
                    selectedLogical = pivotFloorMod(position, pageCount)
                    selectionProgress = progressHolder.updateFromAbsolutePage(
                        absolutePage = position,
                        pageOffset = 0f,
                        settled = true,
                        logicalPageCount = pageCount,
                    )
                    updateActivityTitle(selectedLogical)
                }
            }
        })

        attachNavOverlay(composeNav, pager)
        updateActivityTitle(selectedLogical)
    }

    override fun onDestroyView() {
        detachNavOverlay()
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        updateActivityTitle(selectedLogical)
        overlayNav?.bringToFront()
    }

    private fun attachNavOverlay(composeNav: ComposeView, pager: ViewPager2) {
        val host = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        (composeNav.parent as? ViewGroup)?.removeView(composeNav)

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        )
        // Sit above collapsing content; do not use HideViewOnScrollBehavior.
        ViewCompat.setElevation(composeNav, 12f * resources.displayMetrics.density)
        host.addView(composeNav, lp)
        overlayNav = composeNav

        composeNav.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        composeNav.setContent {
            val context = LocalContext.current
            val dark = isSystemInDarkTheme()
            MaterialExpressiveTheme(
                colorScheme = if (dark) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                },
                motionScheme = MotionScheme.expressive(),
            ) {
                TelegramBottomNavDock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (FloatingNavPaddingStore.heightPx != size.height) {
                                FloatingNavPaddingStore.heightPx = size.height
                            }
                        },
                ) {
                    SettingsFloatingNavBar(
                        selectedIndex = selectedLogical,
                        selectionProgress = selectionProgress,
                        onSelected = { index ->
                            val target = nearestLoopPage(
                                currentPage = pager.currentItem,
                                targetLogical = index,
                                pageCount = SettingsNavItems.size,
                            )
                            if (target != pager.currentItem) {
                                pager.setCurrentItem(target, true)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        composeNav.bringToFront()
    }

    private fun detachNavOverlay() {
        val nav = overlayNav ?: return
        (nav.parent as? ViewGroup)?.removeView(nav)
        overlayNav = null
        FloatingNavPaddingStore.heightPx = 0
    }

    private fun updateActivityTitle(page: Int) {
        val titleRes = when (pivotFloorMod(page, SettingsNavItems.size)) {
            0 -> R.string.tab_library
            else -> R.string.tab_settings
        }
        requireActivity().setTitle(titleRes)
    }

    companion object {
        private const val LOOP_MULTIPLIER = 10_000
    }
}
