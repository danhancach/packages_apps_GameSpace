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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.android.settingslib.widget.SettingsPreferenceGroupAdapter
import androidx.preference.SwitchPreferenceCompat

import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.google.android.material.appbar.CollapsingToolbarLayout

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

    private val navPaddingListener = { applyLibraryListInsets() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Delivery SSOT: FragmentResult song qua Activity recreation (khong Activity lambda).
        parentFragmentManager.setFragmentResultListener(
            SettingsActivity.REQUEST_ADD_GAME_SELECTION,
            this,
        ) { _, bundle ->
            val pkg = bundle.getString(SettingsActivity.KEY_ADD_GAME_PACKAGE)
                ?: return@setFragmentResultListener
            val list = apps
                ?: findPreference<AppListPreferences>("gamespace_game_list")
            list?.useSelectorResult(SettingsActivity.selectorResultOk(pkg))
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.library_preferences, rootKey)
        updateLibraryOptions()
        // Sau reboot / clear sandbox_config — ap lai neu vault dang bat.
        if (gameIconVault.isAvailable() && gameIconVault.enabled) {
            Thread({ gameIconVault.applyForLibrary(true) }, "GameIconVault-apply").start()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val root = inflater.inflate(R.layout.fragment_collapsing_toolbar, container, false)
        val prefContainer = root.findViewById<ViewGroup>(R.id.preference_container)
        val prefView = super.onCreateView(inflater, prefContainer, savedInstanceState)
        prefContainer.addView(prefView)

        val collapsingToolbar = root.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)
        collapsingToolbar.title = getString(R.string.tab_library)

        return root
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> =
        object : SettingsPreferenceGroupAdapter(preferenceScreen, footerDataMap) {
            override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                val preference = getItem(position) ?: return
                (preference.parent as? AppListPreferences)
                    ?.bindCardChromeAfterAdapter(holder, preference)
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
        // Card click = launch; chevron = LibraryModeSelectionPopup (khong PerAppSettings).

        findPreference<Preference>(AppListPreferences.KEY_ADD_GAME)
            ?.setOnPreferenceClickListener {
                val host = activity as? SettingsActivity
                if (host != null) {
                    // In-Activity Miuix submenu; selection qua FragmentResult listener.
                    host.openAddGameSelector()
                } else {
                    // Fallback neu Library khong nam trong SettingsActivity.
                    selectorResult.launch(Intent(context, AppSelectorActivity::class.java))
                }
                true
            }

        applyLibraryListInsets()
        (activity as? SettingsActivity)?.applyStatusBarInset(view)
        FloatingNavPaddingStore.addListener(navPaddingListener)
    }

    override fun onDestroyView() {
        FloatingNavPaddingStore.removeListener(navPaddingListener)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        apps?.updateAppList()
        applyLibraryListInsets()
    }

    /**
     * Library-only width inset.
     *
     * Prior effective outer (card qua hep):
     * - List H pad = listPreferredItemPadding* = 16dp
     *   (`SettingsTheme/res/values-v36/themes_expressive.xml:32-35` →
     *   `settingslib_expressive_space_small1` = 16dp)
     * - + card `layout_marginHorizontal` 16dp
     *   (`library_item.xml` → `gamespace_preference_card_margin_horizontal`)
     * - = 32dp moi ben toi canh card.
     * Sau Preference.onBind, `SettingsPreferenceGroupAdapter.onBindViewHolder`
     * (`SettingsPreferenceGroupAdapter.kt:106-111`) goi `updateBackground` →
     * `setPaddingRelative(mGroupPaddingStart, …)` voi
     * `mGroupPaddingStart = small1 * 2 = 32dp` (`:67-72`, `:241-252`, `:280-281`)
     * — ghi de padding XML hang; KHONG xoa margin. Day la inset NOI dung trong card.
     *
     * Corrected: list H = 0 (chi Library) → outer margin card dung 1×16dp;
     * AppListPreferences restore row pad 16dp sau adapter overwrite.
     * Settings van dung applyFloatingBottomNavPadding (giu H pad theme).
     */
    private fun applyLibraryListInsets() {
        applyFloatingBottomNavPadding()
        val list = listView ?: return
        list.setPadding(0, list.paddingTop, 0, list.paddingBottom)
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
