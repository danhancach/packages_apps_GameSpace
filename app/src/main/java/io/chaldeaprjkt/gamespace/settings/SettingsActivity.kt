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
package io.chaldeaprjkt.gamespace.settings

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.R

@AndroidEntryPoint(CollapsingToolbarBaseActivity::class)
class SettingsActivity : Hilt_SettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTelegramStyleNavigationBars()
        // Dam bao CollapsingToolbar co tieu de ngay tu dau (tranh trang/trong).
        setTitle(R.string.settings_title)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    SettingsPagerFragment()
                )
                .commit()
        }
    }

    /**
     * Transparent gesture-nav bar (no contrast scrim) so [TelegramBottomNavDock] can draw
     * the soft surface gradient. Content draws under the gesture inset (bottom padding 0).
     */
    private fun applyTelegramStyleNavigationBars() {
        val window = window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.isNavigationBarContrastEnforced = false
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT

        val content = findViewById<ViewGroup>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, windowInsets ->
            val insets: Insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.ime()
                    or WindowInsetsCompat.Type.displayCutout(),
            )
            val statusTop = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            // Keep left/right/status padding; allow drawing under the gesture nav.
            v.setPadding(insets.left, statusTop, insets.right, 0)
            (v as ViewGroup).clipToPadding = false
            (v as ViewGroup).clipChildren = false
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(content)
    }
}
