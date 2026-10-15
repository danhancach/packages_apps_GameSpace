/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2026 crDroid Android Project
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
package io.chaldeaprjkt.gamespace.preferences

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder

import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.GameConfig
import io.chaldeaprjkt.gamespace.data.UserGame
import io.chaldeaprjkt.gamespace.settings.PerAppSettingsActivity
import io.chaldeaprjkt.gamespace.utils.GameModeUtils.Companion.describeGameMode
import io.chaldeaprjkt.gamespace.utils.di.ServiceViewEntryPoint
import io.chaldeaprjkt.gamespace.utils.entryPointOf

class AppListPreferences @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    PreferenceCategory(context, attrs), Preference.OnPreferenceClickListener {

    private val apps = mutableListOf<UserGame>()

    private val systemSettings by lazy {
        context.entryPointOf<ServiceViewEntryPoint>().systemSettings()
    }

    private val gameModeUtils by lazy {
        context.entryPointOf<ServiceViewEntryPoint>().gameModeUtils()
    }

    private val gameIconVault by lazy {
        context.entryPointOf<ServiceViewEntryPoint>().gameIconVault()
    }

    private lateinit var registeredAppClickAction: (String) -> Unit

    private var currentAddPrefPosition = 1
    private var currentTotalCount = 2

    init {
        isOrderingAsAdded = false
    }

    // Bo goc nhom the Top/Center/Bottom/Single 20/4dp theo chuan Expressive
    private fun getBackgroundForPosition(position: Int, total: Int): Int = when {
        total <= 1 -> R.drawable.gamespace_preference_bg_single
        position == 0 -> R.drawable.gamespace_preference_bg_top
        position == total - 1 -> R.drawable.gamespace_preference_bg_bottom
        else -> R.drawable.gamespace_preference_bg_center
    }

    private val emptyPref by lazy {
        object : Preference(context) {
            override fun onBindViewHolder(holder: PreferenceViewHolder) {
                super.onBindViewHolder(holder)
                holder.itemView.setBackgroundResource(R.drawable.gamespace_preference_bg_top)
            }
        }.apply {
            key = KEY_EMPTY_STATE
            layoutResource = R.layout.library_empty_item
            isPersistent = false
            isSelectable = false
        }
    }

    private val makeAddPref by lazy {
        object : Preference(context) {
            override fun onBindViewHolder(holder: PreferenceViewHolder) {
                super.onBindViewHolder(holder)
                val bg = getBackgroundForPosition(currentAddPrefPosition, currentTotalCount)
                holder.itemView.setBackgroundResource(bg)
            }
        }.apply {
            title = context.getString(R.string.add_game)
            key = KEY_ADD_GAME
            layoutResource = R.layout.library_add_item
            isPersistent = false
            onPreferenceClickListener = this@AppListPreferences
        }
    }

    private fun getAppInfo(packageName: String): ApplicationInfo? = try {
        val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        context.packageManager.getApplicationInfo(packageName, flags)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun isGameCategory(packageName: String): Boolean =
        getAppInfo(packageName)?.category == ApplicationInfo.CATEGORY_GAME

    private fun readDeniedList(): MutableSet<String> {
        val raw = Settings.System.getStringForUser(
            context.contentResolver, KEY_DENIED_LIST,
            UserHandle.USER_CURRENT
        ) ?: return mutableSetOf()
        return raw.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
    }

    private fun writeDeniedList(set: Set<String>) {
        Settings.System.putStringForUser(
            context.contentResolver,
            KEY_DENIED_LIST,
            set.joinToString(";"),
            UserHandle.USER_CURRENT
        )
    }

    private fun launchGame(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // No-op
        }
    }

    private fun buildGamePref(game: UserGame, position: Int, total: Int): Preference {
        val info = getAppInfo(game.packageName)
        val pm = context.packageManager

        return object : Preference(context) {
            override fun onBindViewHolder(holder: PreferenceViewHolder) {
                super.onBindViewHolder(holder)
                holder.itemView.setBackgroundResource(getBackgroundForPosition(position, total))

                val canLaunch = pm.getLaunchIntentForPackage(game.packageName) != null
                holder.findViewById(R.id.launch_icon)?.let { play ->
                    play.visibility = if (canLaunch) View.VISIBLE else View.GONE
                    play.setOnClickListener { launchGame(game.packageName) }
                }

                holder.findViewById(R.id.settings_icon)?.setOnClickListener {
                    if (::registeredAppClickAction.isInitialized) {
                        registeredAppClickAction(game.packageName)
                    }
                }
            }
        }.apply {
            key = game.packageName
            title = info?.loadLabel(pm)
            summary = context.describeGameMode(game.mode)
            icon = info?.loadIcon(pm)
            layoutResource = R.layout.library_item
            isPersistent = false
            setOnPreferenceClickListener {
                if (::registeredAppClickAction.isInitialized) {
                    registeredAppClickAction(game.packageName)
                }
                true
            }
        }
    }

    fun updateAppList() {
        apps.clear()
        systemSettings.userGames?.let { apps.addAll(it) }

        removeAll()

        val validGames = apps
            .filter { getAppInfo(it.packageName) != null }
            .sortedBy { getAppInfo(it.packageName)?.loadLabel(context.packageManager).toString().lowercase() }

        if (validGames.isEmpty()) {
            // Khi thu vien trong: Empty state o tren (Top), nut Them tro choi o duoi (Bottom)
            currentAddPrefPosition = 1
            currentTotalCount = 2
            addPreference(emptyPref)
            addPreference(makeAddPref)
        } else {
            val total = validGames.size + 1
            currentAddPrefPosition = validGames.size
            currentTotalCount = total
            validGames.forEachIndexed { index, game ->
                addPreference(buildGamePref(game, index, total))
            }
            addPreference(makeAddPref)
        }
    }

    private fun registerApp(packageName: String) {
        // Lift any prior deny so the framework will auto-keep this on reinstall.
        val denied = readDeniedList()
        if (denied.remove(packageName)) writeDeniedList(denied)

        if (apps.none { it.packageName == packageName }) {
            apps.add(UserGame(packageName))
        }
        systemSettings.userGames = apps
        gameModeUtils.setIntervention(packageName, GameConfig.ModeBuilder.build())
        gameIconVault.onGameRegistered(packageName)
        updateAppList()
    }

    private fun unregisterApp(packageName: String) {
        // Persist the user's "no" so the framework's auto-detect skips this package.
        if (isGameCategory(packageName)) {
            val denied = readDeniedList()
            if (denied.add(packageName)) writeDeniedList(denied)
        }

        apps.removeIf { it.packageName == packageName }
        systemSettings.userGames = apps
        gameModeUtils.setIntervention(packageName, null)
        gameIconVault.onGameUnregistered(packageName)
        updateAppList()
    }

    override fun onAttached() {
        super.onAttached()
        updateAppList()
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        return true
    }

    fun onRegisteredAppClick(action: (String) -> Unit) {
        registeredAppClickAction = action
    }

    fun usePerAppResult(result: ActivityResult?) {
        result?.takeIf { it.resultCode == Activity.RESULT_OK }
            ?.data?.getStringExtra(PerAppSettingsActivity.PREF_UNREGISTER)
            ?.let { unregisterApp(it) }
    }

    fun useSelectorResult(result: ActivityResult?) {
        result?.takeIf { it.resultCode == Activity.RESULT_OK }
            ?.data?.getStringExtra(EXTRA_APP)
            ?.let { registerApp(it) }
    }

    companion object {
        const val KEY_ADD_GAME = "add_game"
        const val KEY_EMPTY_STATE = "empty_state"
        const val EXTRA_APP = "selected_app"
        const val KEY_DENIED_LIST = "gamespace_denied_list"
        const val KEY_AUTO_GAME_DETECT = "gamespace_auto_game_detect"
    }
}
