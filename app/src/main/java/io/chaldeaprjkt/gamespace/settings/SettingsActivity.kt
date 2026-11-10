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

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.ActivityResult
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.Insets
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.preferences.AppListPreferences
import io.chaldeaprjkt.gamespace.preferences.appselector.AppSelectorFragment
import io.chaldeaprjkt.gamespace.ui.motion.AddGamePredictiveBack
import io.chaldeaprjkt.gamespace.ui.motion.MiuixPageMotion
import io.chaldeaprjkt.gamespace.ui.motion.MiuixPageProgressDriver
import io.chaldeaprjkt.gamespace.ui.nav.ContinuousSelectionProgressHolder
import io.chaldeaprjkt.gamespace.ui.nav.SettingsFloatingNavBar
import io.chaldeaprjkt.gamespace.ui.nav.SettingsNavItems
import io.chaldeaprjkt.gamespace.ui.nav.TelegramBottomNavDock

@AndroidEntryPoint(AppCompatActivity::class)
class SettingsActivity : Hilt_SettingsActivity() {

    private val progressHolder = ContinuousSelectionProgressHolder()
    private var selectionProgress by mutableFloatStateOf(0f)
    private var selectedLogical by mutableIntStateOf(0)
    private var isPagerDragging by mutableStateOf(false)

    private lateinit var pager: ViewPager2
    private lateinit var parentLayer: View
    private lateinit var childHost: ViewGroup
    private lateinit var navScrim: View

    private var addGameDesiredOpen: Boolean = false
    private var addGameGeneration: Long = 0L
    /** Predictive gesture session; null khi khong scrub / da invalidate. */
    private var predictiveBackSession: AddGamePredictiveBack.Session? = null
    /** Exactly-once flag; delivery qua FragmentResult (song qua recreation). */
    private var selectionConsumed: Boolean = false

    private val addGameBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            // Mot callback route: acquire ownership truoc frame scrub dau.
            val session = AddGamePredictiveBack.beginSession(
                desiredOpen = addGameDesiredOpen,
                generation = addGameGeneration,
                currentPageProgress = progressDriver.progress,
            ) ?: return
            predictiveBackSession = session
            // scrubTo huy animator open/close; KHONG onSettled (giu child).
            progressDriver.scrubTo(session.anchorPageProgress, addGameGeneration)
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            val target = AddGamePredictiveBack.scrubPageProgress(
                session = predictiveBackSession,
                desiredOpen = addGameDesiredOpen,
                currentGeneration = addGameGeneration,
                backProgress = backEvent.progress,
            ) ?: return
            progressDriver.scrubTo(target, addGameGeneration)
        }

        override fun handleOnBackCancelled() {
            val session = predictiveBackSession
            val releaseVelocity = AddGamePredictiveBack.releaseVelocity(
                session = session,
                desiredOpen = addGameDesiredOpen,
                currentProgress = progressDriver.progress,
                target = 1f,
                swipeVelocity = progressDriver.velocity,
            )
            val decision = AddGamePredictiveBack.onCancelled(
                session = session,
                desiredOpen = addGameDesiredOpen,
                currentGeneration = addGameGeneration,
            )
            predictiveBackSession = AddGamePredictiveBack.invalidateSession()
            if (decision != AddGamePredictiveBack.CancelDecision.SpringFullyOpen) return
            // Spring ve fully open; ke thua dong hoc vuot
            progressDriver.animateTo(
                targetValue = 1f,
                generation = addGameGeneration,
                initialVelocity = releaseVelocity,
                forceSpring = true,
            )
        }

        override fun handleOnBackPressed() {
            val session = predictiveBackSession
            val releaseVelocity = AddGamePredictiveBack.releaseVelocity(
                session = session,
                desiredOpen = addGameDesiredOpen,
                currentProgress = progressDriver.progress,
                target = 0f,
                swipeVelocity = progressDriver.velocity,
            )
            predictiveBackSession = AddGamePredictiveBack.invalidateSession()
            if (AddGamePredictiveBack.onCommitted(addGameDesiredOpen) ==
                AddGamePredictiveBack.CommitDecision.CloseRoute
            ) {
                closeAddGameSelector(initialVelocity = releaseVelocity)
            }
        }
    }

    private val progressDriver = MiuixPageProgressDriver(
        onProgress = { p -> applyAddGameProgress(p) },
        onSettled = { p, gen -> onAddGameSettled(p, gen) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // Monet theo kieu Updater / ReSukiSU: applyStyle ThemeOverlay tren parent Theme.Material3.DayNight
        applyDynamicColorOverlay()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applyTelegramStyleNavigationBars()
        parentLayer = findViewById(R.id.settings_parent_layer)
        childHost = findViewById(R.id.settings_child_host)
        navScrim = findViewById(R.id.settings_nav_scrim)
        onBackPressedDispatcher.addCallback(this, addGameBackCallback)
        setupPagerAndNav(savedInstanceState)
        restoreAddGameRoute(savedInstanceState)
        preloadAddGameFragment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ADD_GAME_OPEN, addGameDesiredOpen)
        outState.putLong(STATE_ADD_GAME_GENERATION, addGameGeneration)
        outState.putBoolean(STATE_ADD_GAME_CONSUMED, selectionConsumed)
        outState.putFloat(STATE_ADD_GAME_PROGRESS, progressDriver.progress)
    }

    override fun onDestroy() {
        predictiveBackSession = AddGamePredictiveBack.invalidateSession()
        progressDriver.destroy()
        FloatingNavPaddingStore.heightPx = 0
        super.onDestroy()
    }

    /**
     * Mo Add-game submenu (Miuix page transition) trong Activity.
     * Selection delivery: FragmentResult [REQUEST_ADD_GAME_SELECTION] (LibraryFragment listen);
     * cancel khong emit result.
     */
    fun openAddGameSelector() {
        predictiveBackSession = AddGamePredictiveBack.invalidateSession()
        selectionConsumed = false
        addGameGeneration += 1L
        addGameDesiredOpen = true
        addGameBackCallback.isEnabled = true
        ensureAddGameFragment(immediate = true)
        childHost.visibility = View.VISIBLE
        childHost.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        navScrim.visibility = View.VISIBLE
        pager.isUserInputEnabled = false
        val frag = supportFragmentManager.findFragmentByTag(TAG_ADD_GAME) as? AppSelectorFragment
        frag?.onRouteOpened()
        // Chan parent ngay khi route doi dich, truoc frame dau ValueAnimator.
        applyAddGameProgress(progressDriver.progress)
        progressDriver.animateTo(1f, addGameGeneration)
    }

    fun closeAddGameSelector(initialVelocity: Float? = null) {
        predictiveBackSession = AddGamePredictiveBack.invalidateSession()
        if (!addGameDesiredOpen && progressDriver.progress <= MiuixPageMotion.SettleThreshold) {
            return
        }
        addGameDesiredOpen = false
        addGameBackCallback.isEnabled = false
        pager.isUserInputEnabled = true
        val frag = supportFragmentManager.findFragmentByTag(TAG_ADD_GAME) as? AppSelectorFragment
        frag?.onRouteClosing()
        // Release the scrim before the next animation frame so a revealed Add game
        // card can retarget this closing animation without waiting for settle.
        applyAddGameProgress(progressDriver.progress)
        progressDriver.animateTo(0f, addGameGeneration, initialVelocity = initialVelocity)
    }

    /**
     * Child selection — FragmentResult dung 1 lan roi close route.
     * @return true neu consume (embedded); false → AppSelectorActivity setResult path.
     */
    fun consumeAddGameSelection(packageName: String): Boolean {
        // Child van con hien trong pha pop nhung route da dong: bo qua tap muon.
        if (!addGameDesiredOpen) {
            return false
        }
        if (selectionConsumed) {
            closeAddGameSelector()
            return true
        }
        selectionConsumed = true
        // Route-level delivery: song qua Activity recreation (khong lambda field).
        supportFragmentManager.setFragmentResult(
            REQUEST_ADD_GAME_SELECTION,
            bundleOf(KEY_ADD_GAME_PACKAGE to packageName),
        )
        closeAddGameSelector()
        return true
    }

    fun isAddGameRouteActive(): Boolean =
        addGameDesiredOpen || progressDriver.progress > MiuixPageMotion.SettleThreshold

    private fun restoreAddGameRoute(savedInstanceState: Bundle?) {
        val open = savedInstanceState?.getBoolean(STATE_ADD_GAME_OPEN) == true
        val savedProgress = savedInstanceState?.getFloat(STATE_ADD_GAME_PROGRESS) ?: 0f
        val isMidPop = !open && savedProgress > MiuixPageMotion.SettleThreshold
        val action = MiuixPageMotion.restoreRouteAction(
            savedDesiredOpen = open,
            hasAddGameFragment = isMidPop,
        )
        selectionConsumed = savedInstanceState?.getBoolean(STATE_ADD_GAME_CONSUMED) == true
        when (action) {
            MiuixPageMotion.RestoreRouteAction.Root -> {
                applyAddGameProgress(0f)
                childHost.visibility = View.INVISIBLE
                childHost.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                navScrim.visibility = View.GONE
                navScrim.isClickable = false
                navScrim.isFocusable = false
            }
            MiuixPageMotion.RestoreRouteAction.SnapOpen -> {
                addGameGeneration =
                    savedInstanceState?.getLong(STATE_ADD_GAME_GENERATION)?.coerceAtLeast(1L)
                        ?: 1L
                addGameDesiredOpen = true
                addGameBackCallback.isEnabled = true
                ensureAddGameFragment(immediate = true)
                childHost.visibility = View.VISIBLE
                childHost.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                navScrim.visibility = View.VISIBLE
                pager.isUserInputEnabled = false
                // Recreate: snap open (khong replay tween).
                progressDriver.snapTo(1f, addGameGeneration)
            }
            MiuixPageMotion.RestoreRouteAction.FinishClose -> {
                // Mid-pop recreate: settle cleanup, KHONG reopen selector.
                addGameGeneration =
                    savedInstanceState?.getLong(STATE_ADD_GAME_GENERATION)?.coerceAtLeast(1L)
                        ?: 1L
                addGameDesiredOpen = false
                addGameBackCallback.isEnabled = false
                pager.isUserInputEnabled = true
                ensureAddGameFragment(immediate = true)
                childHost.visibility = View.VISIBLE
                progressDriver.snapTo(0f, addGameGeneration)
            }
        }
    }

    private fun preloadAddGameFragment() {
        parentLayer.post {
            ensureAddGameFragment(immediate = false)
        }
    }

    private fun ensureAddGameFragment(immediate: Boolean = false) {
        if (supportFragmentManager.findFragmentByTag(TAG_ADD_GAME) != null) return
        val tx = supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_child_host, AppSelectorFragment(), TAG_ADD_GAME)
        if (immediate) {
            tx.commitNow()
        } else {
            tx.commitAllowingStateLoss()
        }
    }

    private fun applyAddGameProgress(p: Float) {
        val width = parentLayer.width.toFloat().let { w ->
            if (w > 0f) w else resources.displayMetrics.widthPixels.toFloat()
        }
        val rtl = parentLayer.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val parent = MiuixPageMotion.parentLayer(p, width, rtl)
        val child = MiuixPageMotion.childLayer(p, width, rtl)
        parentLayer.translationX = parent.translationX
        parentLayer.alpha = parent.alpha
        childHost.translationX = child.translationX
        childHost.alpha = child.alpha
        val scrim = MiuixPageMotion.scrimAlpha(p)
        val blockParent = MiuixPageMotion.shouldBlockParentInput(addGameDesiredOpen)
        navScrim.alpha = scrim
        navScrim.visibility = if (!blockParent && scrim <= 0.001f &&
            p <= MiuixPageMotion.SettleThreshold
        ) {
            View.GONE
        } else {
            View.VISIBLE
        }
        // Keep drawing the scrim during close, but let revealed parent bounds receive taps.
        navScrim.isClickable = blockParent
        navScrim.isFocusable = blockParent
        // Restore parent accessibility when close is committed.
        parentLayer.importantForAccessibility =
            if (blockParent) {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            }
    }

    private fun onAddGameSettled(progress: Float, generation: Long) {
        if (MiuixPageMotion.shouldCleanupChild(
                settledProgress = progress,
                desiredOpen = addGameDesiredOpen,
                completedGeneration = generation,
                currentGeneration = addGameGeneration,
            )
        ) {
            // Giu fragment trong childHost; chi an visibility va khoa tuong tac
            childHost.visibility = View.INVISIBLE
            childHost.translationX = 0f
            childHost.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            navScrim.visibility = View.GONE
            navScrim.isClickable = false
            navScrim.isFocusable = false
            parentLayer.translationX = 0f
            parentLayer.alpha = 1f
            parentLayer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            pager.isUserInputEnabled = true
            addGameBackCallback.isEnabled = false
            selectionConsumed = false
            val frag = supportFragmentManager.findFragmentByTag(TAG_ADD_GAME) as? AppSelectorFragment
            frag?.onRouteClosed()
        }
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

    var statusBarHeightPx: Int = 0
        private set

    fun applyStatusBarInset(view: View?) {
        val bar = view?.findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar) ?: return
        if (statusBarHeightPx > 0) {
            bar.setPadding(bar.paddingLeft, statusBarHeightPx, bar.paddingRight, bar.paddingBottom)
        }
    }

    private fun dispatchStatusBarInset() {
        if (statusBarHeightPx <= 0) return
        supportFragmentManager.fragments.forEach { frag ->
            applyStatusBarInset(frag.view)
        }
    }

    /**
     * Trong suot thanh dieu huong cu chi, cho phep TelegramBottomNavDock ve gradient soft surface.
     * Transition viewport va scrim phu toan bo Status Bar; insets duoc phan bo rieng cho AppBar.
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
            statusBarHeightPx = statusTop
            // Khong pad top o root de transition viewport va scrim phu toan bo Status Bar
            v.setPadding(insets.left, 0, insets.right, 0)
            (v as ViewGroup).clipToPadding = false
            (v as ViewGroup).clipChildren = false
            dispatchStatusBarInset()
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setupPagerAndNav(savedInstanceState: Bundle?) {
        pager = findViewById(R.id.settings_pager)
        val composeNav = findViewById<ComposeView>(R.id.settings_nav_compose)

        // Finite tabs: pageCount = SettingsNavItems.size (ReSukiSU / Updater pattern).
        // Khong LOOP_MULTIPLIER / nearestLoopPage — tap Library↔Settings = 0↔1.
        val pageCount = SettingsNavItems.size

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = pageCount

            override fun createFragment(position: Int): Fragment =
                when (position.coerceIn(0, pageCount - 1)) {
                    0 -> LibraryFragment()
                    else -> SettingsFragment()
                }

            // On dinh theo ten tab (Updater key = tab.name); tranh identity lap modulo.
            override fun getItemId(position: Int): Long =
                SettingsNavItems[position.coerceIn(0, pageCount - 1)].ordinal.toLong()

            override fun containsItem(itemId: Long): Boolean =
                itemId in 0 until pageCount.toLong()
        }
        // Giu ca 2 fragment — scroll = native RV/LayoutManager + Coordinator/AppBar.
        pager.offscreenPageLimit = 1
        pager.isUserInputEnabled = true
        // Chi set page 0 lan tao moi; recreate de ViewPager2/Fragment saved state.
        if (savedInstanceState == null) {
            pager.setCurrentItem(0, false)
        }
        val initial = pager.currentItem.coerceIn(0, pageCount - 1)
        selectedLogical = initial
        selectionProgress = progressHolder.update(initial, 0f)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int,
            ) {
                // Continuous nav = position + offset (Updater selectedPagePosition).
                selectionProgress = progressHolder.update(position, positionOffset)
                selectedLogical = if (positionOffset >= 0.5f) {
                    (position + 1).coerceAtMost(pageCount - 1)
                } else {
                    position.coerceIn(0, pageCount - 1)
                }
            }

            override fun onPageSelected(position: Int) {
                selectedLogical = position.coerceIn(0, pageCount - 1)
                if (pager.scrollState == ViewPager2.SCROLL_STATE_IDLE) {
                    selectionProgress = progressHolder.update(position, 0f)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                isPagerDragging = (state == ViewPager2.SCROLL_STATE_DRAGGING)
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val position = pager.currentItem.coerceIn(0, pageCount - 1)
                    selectedLogical = position
                    selectionProgress = progressHolder.update(position, 0f)
                }
            }
        })

        // Floating pill nav: dat truc tiep tren parent layer (dich cung submenu).
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
                        isDragging = isPagerDragging,
                        onSelected = { index ->
                            if (isAddGameRouteActive()) return@SettingsFloatingNavBar
                            val target = index.coerceIn(0, pageCount - 1)
                            if (target != pager.currentItem) {
                                pager.setCurrentItem(target, true)
                            }
                        },
                        // Pill thu hep theo 2 tab; dock van full-width cho fade/gesture inset.
                        modifier = Modifier,
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG_ADD_GAME = "add_game_selector"
        private const val STATE_ADD_GAME_OPEN = "settings_add_game_open"
        private const val STATE_ADD_GAME_GENERATION = "settings_add_game_generation"
        private const val STATE_ADD_GAME_CONSUMED = "settings_add_game_consumed"
        private const val STATE_ADD_GAME_PROGRESS = "settings_add_game_progress"

        /** FragmentResult key — LibraryFragment listen; song qua recreation. */
        const val REQUEST_ADD_GAME_SELECTION = "gamespace_add_game_selection"
        const val KEY_ADD_GAME_PACKAGE = "package_name"

        /** Helper: ActivityResult OK + EXTRA_APP cho AppListPreferences.useSelectorResult. */
        fun selectorResultOk(packageName: String): ActivityResult =
            ActivityResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppListPreferences.EXTRA_APP, packageName),
            )
    }
}
