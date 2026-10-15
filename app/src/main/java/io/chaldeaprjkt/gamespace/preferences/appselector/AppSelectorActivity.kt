/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2025 AxionOS
 * Copyright (C) 2026 crDroid Android Project
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
package io.chaldeaprjkt.gamespace.preferences.appselector

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.R

@AndroidEntryPoint(AppCompatActivity::class)
class AppSelectorActivity : Hilt_AppSelectorActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDynamicColorOverlay()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub_settings)
        applyEdgeToEdge()

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.content_frame,
                    AppSelectorFragment()
                )
                .commit()
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

    private fun applyEdgeToEdge() {
        val window = window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.isNavigationBarContrastEnforced = false
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT

        val root = findViewById<ViewGroup>(R.id.sub_settings_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, windowInsets ->
            val insets: Insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.ime()
                    or WindowInsetsCompat.Type.displayCutout(),
            )
            val statusTop = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(insets.left, statusTop, insets.right, insets.bottom)
            (v as ViewGroup).clipToPadding = false
            (v as ViewGroup).clipChildren = false
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }
}
