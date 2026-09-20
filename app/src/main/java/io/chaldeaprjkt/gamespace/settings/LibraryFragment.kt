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
package io.chaldeaprjkt.gamespace.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat

import com.android.settingslib.widget.SettingsBasePreferenceFragment

import dagger.hilt.android.AndroidEntryPoint

import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.GameIconVault
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.preferences.AppListPreferences
import io.chaldeaprjkt.gamespace.preferences.appselector.AppSelectorActivity

import javax.inject.Inject

@AndroidEntryPoint(SettingsBasePreferenceFragment::class)
class LibraryFragment : Hilt_LibraryFragment(), Preference.OnPreferenceChangeListener {

    private var apps: AppListPreferences? = null

    @Inject
    lateinit var gameIconVault: GameIconVault

    @Inject
    lateinit var systemSettings: SystemSettings

    private val selectorResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            apps?.useSelectorResult(it)
        }

    private val perAppResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            apps?.usePerAppResult(it)
        }

    private val navPaddingListener = { applyFloatingBottomNavPadding() }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.library_preferences, rootKey)
        updateLibraryOptions()
        // Sau reboot / clear sandbox_config — ap lai neu vault dang bat.
        if (gameIconVault.isAvailable() && gameIconVault.enabled) {
            Thread({ gameIconVault.applyForLibrary(true) }, "GameIconVault-apply").start()
        }
    }

    private fun updateLibraryOptions() {
        val vault = findPreference<SwitchPreferenceCompat>(AppSettings.KEY_ICON_VAULT)
        if (vault != null) {
            if (!gameIconVault.isAvailable()) {
                preferenceScreen.findPreference<Preference>("library_options")
                    ?.let { cat ->
                        (cat as? androidx.preference.PreferenceGroup)?.removePreference(vault)
                    } ?: preferenceScreen.removePreference(vault)
            } else {
                vault.isChecked = gameIconVault.enabled
                vault.onPreferenceChangeListener = this
            }
        }

        findPreference<SwitchPreferenceCompat>(AppListPreferences.KEY_AUTO_GAME_DETECT)?.let { pref ->
            pref.isChecked = systemSettings.autoGameDetect
            pref.onPreferenceChangeListener = this
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apps = findPreference("gamespace_game_list")
        apps?.onRegisteredAppClick { pkg ->
            perAppResult.launch(
                Intent(context, PerAppSettingsActivity::class.java).apply {
                    putExtra(PerAppSettingsActivity.EXTRA_PACKAGE, pkg)
                }
            )
        }

        findPreference<Preference>(AppListPreferences.KEY_ADD_GAME)
            ?.setOnPreferenceClickListener {
                selectorResult.launch(Intent(context, AppSelectorActivity::class.java))
                true
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
        apps?.updateAppList()
        applyFloatingBottomNavPadding()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        when (preference.key) {
            AppSettings.KEY_ICON_VAULT -> {
                // Ap dung tren background — tranh block UI; framework da fix deadlock.
                val enabled = newValue as Boolean
                Thread({ gameIconVault.enabled = enabled }, "GameIconVault").start()
                return true
            }
            AppListPreferences.KEY_AUTO_GAME_DETECT -> {
                systemSettings.autoGameDetect = newValue as Boolean
                return true
            }
        }
        return false
    }
}
