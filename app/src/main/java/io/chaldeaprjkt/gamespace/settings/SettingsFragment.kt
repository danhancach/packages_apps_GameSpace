/*
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
package io.chaldeaprjkt.gamespace.settings

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemProperties
import android.os.Vibrator
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat

import com.android.settingslib.widget.SettingsBasePreferenceFragment

import dagger.hilt.android.AndroidEntryPoint

import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.GameOptimizationManager
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.preferences.QuickStartAppPreference
import io.chaldeaprjkt.gamespace.preferences.QuickStartAppPreferenceDialogFragment

import lineageos.hardware.LineageHardwareManager

import javax.inject.Inject

@AndroidEntryPoint(SettingsBasePreferenceFragment::class)
class SettingsFragment : Hilt_SettingsFragment(),
    QuickStartAppPreferenceDialogFragment.QuickStartAppListener,
    Preference.OnPreferenceChangeListener {

    @Inject
    lateinit var gameOptimization: GameOptimizationManager

    private val navPaddingListener = { applyFloatingBottomNavPadding() }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)
        updatePreferences()
    }

    private fun hasVibrator(): Boolean {
        val vibrator = context?.getSystemService(Vibrator::class.java)
        return vibrator?.hasVibrator() == true
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        val pm = context?.packageManager ?: return false
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isHspcAvailable(): Boolean =
        isPackageInstalled(SystemSettings.HSPC_PACKAGE)

    private fun isHighTouchAvailable(): Boolean {
        val ctx = context ?: return false
        return try {
            LineageHardwareManager.getInstance(ctx)
                .isSupported(LineageHardwareManager.FEATURE_HIGH_TOUCH_POLLING_RATE)
        } catch (_: Throwable) {
            isPackageInstalled(SystemSettings.TOUCH_PACKAGE)
        }
    }

    private fun removeInGamePreference(key: String) {
        val category = findPreference<PreferenceCategory>("in_game_preferences") ?: return
        findPreference<Preference>(key)?.let { category.removePreference(it) }
    }

    private fun updatePreferences() {
        val isBypassSupported =
            Build.MANUFACTURER.equals("Google", ignoreCase = true) ||
            SystemProperties.getBoolean("persist.sys.battery_bypass_supported", false)

        if (!isBypassSupported) {
            removeInGamePreference("bypass_charge_enabled")
        }
        if (!hasVibrator()) {
            removeInGamePreference("gamespace_pulse_bass_haptics_disabled")
        }
        if (!isHspcAvailable()) {
            removeInGamePreference(AppSettings.KEY_AUTO_HSPC)
        }
        if (!isHighTouchAvailable()) {
            removeInGamePreference(AppSettings.KEY_AUTO_HIGH_TOUCH)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findPreference<SwitchPreferenceCompat>("game_memory_management")?.apply {
            isChecked = gameOptimization.isMemoryManagementEnabled
            onPreferenceChangeListener = this@SettingsFragment
        }

        findPreference<SwitchPreferenceCompat>("game_cache_management")?.apply {
            isChecked = gameOptimization.isCacheManagementEnabled
            onPreferenceChangeListener = this@SettingsFragment
        }

        applyFloatingBottomNavPadding()
        FloatingNavPaddingStore.addListener(navPaddingListener)
    }

    override fun onDestroyView() {
        FloatingNavPaddingStore.removeListener(navPaddingListener)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        // Title owned by SettingsPagerFragment (both pages stay resumed with VP2).
        applyFloatingBottomNavPadding()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is QuickStartAppPreference) {
            val dialogFragment =
                QuickStartAppPreferenceDialogFragment.newInstance(preference.key)
            dialogFragment.setListener(this)
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, "QuickStartAppPreferenceDialogFragment")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun getSavedQuickStartApps(): String {
        val prefs = preferenceManager.sharedPreferences ?: return ""
        return prefs.getString(AppSettings.KEY_QUICK_START_APPS, "") ?: ""
    }

    override fun saveQuickStartApps(apps: String) { /* no-op */ }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        when (preference.key) {
            "game_memory_management" -> {
                gameOptimization.isMemoryManagementEnabled = newValue as Boolean
                return true
            }
            "game_cache_management" -> {
                gameOptimization.isCacheManagementEnabled = newValue as Boolean
                return true
            }
        }
        return false
    }
}
