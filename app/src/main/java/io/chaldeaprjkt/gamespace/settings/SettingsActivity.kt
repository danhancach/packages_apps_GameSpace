/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2026 crDroid Android Project
 * Copyright (C) 2025-2026 AxionOS
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

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
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
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.ui.nav.ContinuousSelectionProgressHolder
import io.chaldeaprjkt.gamespace.ui.nav.SettingsFloatingNavBar
import io.chaldeaprjkt.gamespace.ui.nav.SettingsNavItems
import io.chaldeaprjkt.gamespace.ui.nav.TelegramBottomNavDock
import io.chaldeaprjkt.gamespace.ui.nav.nearestLoopPage
import io.chaldeaprjkt.gamespace.ui.nav.pivotFloorMod
import io.chaldeaprjkt.gamespace.ui.nav.updateFromAbsolutePage
import kotlin.math.abs

@AndroidEntryPoint(AppCompatActivity::class)
class SettingsActivity : Hilt_SettingsActivity() {

    private val progressHolder = ContinuousSelectionProgressHolder()
    private var selectionProgress by mutableFloatStateOf(0f)
    private var selectedLogical by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Monet theo kieu Updater / ReSukiSU: applyStyle ThemeOverlay tren parent Theme.Material3.DayNight
        applyDynamicColorOverlay()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applyTelegramStyleNavigationBars()
        setupPagerAndNav()
    }

    override fun onDestroy() {
        FloatingNavPaddingStore.heightPx = 0
        super.onDestroy()
    }

    private fun applyDynamicColorOverlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        theme.applyStyle(
            com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_DayNight,
            true,
        )
    }

    /**
     * Trong suot thanh dieu huong cu chi, cho phep TelegramBottomNavDock ve gradient soft surface.
     */
    private fun applyTelegramStyleNavigationBars() {
        val window = window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.isNavigationBarContrastEnforced = false
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT

        val root = findViewById<ViewGroup>(R.id.settings_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, windowInsets ->
            val insets: Insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.ime()
                    or WindowInsetsCompat.Type.displayCutout(),
            )
            val statusTop = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            // Pad top cho status bar; de trong bottom cho floating nav
            v.setPadding(insets.left, statusTop, insets.right, 0)
            (v as ViewGroup).clipToPadding = false
            (v as ViewGroup).clipChildren = false
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setupPagerAndNav() {
        val pager = findViewById<ViewPager2>(R.id.settings_pager)
        val composeNav = findViewById<ComposeView>(R.id.settings_nav_compose)

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
                }
            }
        })

        // Floating pill nav: dat truc tiep tren Activity goc, khong can reparent hack
        ViewCompat.setElevation(composeNav, 12f * resources.displayMetrics.density)
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
    }

    companion object {
        private const val LOOP_MULTIPLIER = 10_000
    }
}
