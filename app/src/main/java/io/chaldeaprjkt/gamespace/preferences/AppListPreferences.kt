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
import io.chaldeaprjkt.gamespace.ui.popup.LibraryModeSelectionPopup
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
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

    /** Add luon first (position 0); empty/game ben duoi. */
    private var currentAddPrefPosition = 0
    private var currentEmptyPrefPosition = 1
    private var currentTotalCount = 2
    private var activePopup: LibraryModeSelectionPopup.Handle? = null
    /** Snapshot keys lan rebuild gan nhat — tranh removeAll khi khong doi. */
    private var lastKeySnapshot: List<String> = emptyList()

    init {
        // order field (LibraryListOrder) quyet dinh — khong title-sort chen Add.
        isOrderingAsAdded = false
    }

    // Bo goc nhom the Top/Center/Bottom/Single 20/4dp theo chuan Expressive
    private fun getBackgroundForPosition(position: Int, total: Int): Int = when {
        total <= 1 -> R.drawable.gamespace_preference_bg_single
        position == 0 -> R.drawable.gamespace_preference_bg_top
        position == total - 1 -> R.drawable.gamespace_preference_bg_bottom
        else -> R.drawable.gamespace_preference_bg_center
    }

    /**
     * Preference con — goi notifyChanged() protected tu chinh instance
     * (PreferenceCategory khong goi duoc tren Preference khac).
     */
    private open class LibraryChromePreference(context: Context) : Preference(context) {
        fun refreshChrome() {
            notifyChanged()
        }
    }

    /**
     * Adapter overwrite bg/pad sau onBind — restore Expressive card (xem LibraryFragment).
     * Game row: padH=0 — insets nam trong child zone de weight 2/1 = full-card 1/3 hit.
     */
    private fun restoreLibraryCardChrome(
        holder: PreferenceViewHolder,
        backgroundRes: Int,
        horizontalPadPx: Int? = null,
    ) {
        val padH = horizontalPadPx ?: context.resources.getDimensionPixelSize(
            R.dimen.gamespace_preference_row_padding_start,
        )
        holder.itemView.setBackgroundResource(backgroundRes)
        holder.itemView.setPaddingRelative(
            padH, holder.itemView.paddingTop, padH, holder.itemView.paddingBottom,
        )
    }

    /** Called synchronously after SettingsLib has applied its generic group chrome. */
    internal fun bindCardChromeAfterAdapter(holder: PreferenceViewHolder, preference: Preference) {
        // library_item: padH=0 (child insets); Add/Empty giu 16dp root.
        val isGameRow = holder.findViewById(R.id.mode_popup_trigger) != null
        restoreLibraryCardChrome(
            holder,
            getBackgroundForPosition(preference.order, currentTotalCount),
            horizontalPadPx = if (isGameRow) 0 else null,
        )
    }

    private val emptyPref by lazy {
        object : LibraryChromePreference(context) {
            override fun onBindViewHolder(holder: PreferenceViewHolder) {
                super.onBindViewHolder(holder)
                // Empty sau Add → thuong Bottom khi total=2.
                restoreLibraryCardChrome(
                    holder,
                    getBackgroundForPosition(currentEmptyPrefPosition, currentTotalCount),
                )
            }
        }.apply {
            key = KEY_EMPTY_STATE
            layoutResource = R.layout.library_empty_item
            isPersistent = false
            isSelectable = false
        }
    }

    private val makeAddPref by lazy {
        object : LibraryChromePreference(context) {
            override fun onBindViewHolder(holder: PreferenceViewHolder) {
                super.onBindViewHolder(holder)
                restoreLibraryCardChrome(
                    holder,
                    getBackgroundForPosition(currentAddPrefPosition, currentTotalCount),
                )
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

    private fun isRegistered(packageName: String): Boolean {
        if (apps.any { it.packageName == packageName }) return true
        return systemSettings.userGames.any { it.packageName == packageName }
    }

    private fun liveGame(packageName: String): UserGame? {
        apps.firstOrNull { it.packageName == packageName }?.let { return it }
        return systemSettings.userGames.firstOrNull { it.packageName == packageName }
    }

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

    private fun launchGame(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * LSPosed DropDownMenu selection popup (LibraryModeSelectionPopup) — khong PopupMenu.
     * Re-read registration luc show / action.
     */
    private fun showGameOptionsPopup(anchor: View, packageName: String) {
        activePopup?.forceCleanup()
        val installed = getAppInfo(packageName) != null
        val registered = isRegistered(packageName)
        val game = liveGame(packageName)
        if (!registered) {
            updateAppList()
            return
        }

        val modes = if (installed && game != null) {
            val entries = context.resources.getStringArray(R.array.per_app_preferred_mode_entries)
            val values = context.resources.getStringArray(R.array.per_app_preferred_mode_values)
            values.mapIndexedNotNull { index, value ->
                val mode = value.toIntOrNull() ?: return@mapIndexedNotNull null
                LibraryModeSelectionPopup.ModeOption(
                    modeId = mode,
                    label = entries.getOrElse(index) { value },
                )
            }
        } else {
            emptyList()
        }
        val selected = game?.mode ?: -1
        // Chi "Remove from library" / "Xoa khoi thu vien" — khong kem ten app.
        val removeLabel = context.getString(R.string.library_remove_from_library)

        val detachHolder = arrayOfNulls<View.OnAttachStateChangeListener>(1)
        val handle = LibraryModeSelectionPopup.show(
            anchor = anchor,
            modes = modes,
            selectedModeId = selected,
            removeLabel = removeLabel,
            onMode = { modeId ->
                if (getAppInfo(packageName) != null && isRegistered(packageName)) {
                    updatePreferredMode(packageName, modeId)
                } else {
                    updateAppList()
                }
            },
            onRemove = {
                if (isRegistered(packageName)) {
                    unregisterApp(packageName)
                } else {
                    updateAppList()
                }
            },
            onDismiss = {
                detachHolder[0]?.let { anchor.removeOnAttachStateChangeListener(it) }
                if (activePopup != null) activePopup = null
            },
        ) ?: return

        val detachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                // Teardown — force, khong choi close anim.
                handle.forceCleanup()
                v.removeOnAttachStateChangeListener(this)
            }
        }
        detachHolder[0] = detachListener
        anchor.addOnAttachStateChangeListener(detachListener)
        activePopup = handle
    }

    private fun updatePreferredMode(packageName: String, mode: Int) {
        if (getAppInfo(packageName) == null || !isRegistered(packageName)) {
            updateAppList()
            return
        }
        gameModeUtils.setGameModeFor(packageName, systemSettings, mode)
        updateAppList()
    }

    private fun buildGamePref(game: UserGame, position: Int, total: Int): Preference {
        val info = getAppInfo(game.packageName)
        val pm = context.packageManager
        val installed = info != null
        val pkg = game.packageName

        return object : Preference(context) {
            override fun onBindViewHolder(holder: PreferenceViewHolder) {
                super.onBindViewHolder(holder)
                // padH=0: full-card 2/1; inset trong launch_zone / mode_popup_trigger.
                restoreLibraryCardChrome(
                    holder,
                    getBackgroundForPosition(position, total),
                    horizontalPadPx = 0,
                )

                // Mode truoc glyph; a11y gom mode hien tai; full-height touch.
                val trigger = holder.findViewById(R.id.mode_popup_trigger)
                val modeLabel = summary?.toString().orEmpty()
                val modeTitle = context.getString(R.string.per_app_mode_title)
                trigger?.contentDescription = if (modeLabel.isNotEmpty()) {
                    "$modeTitle: $modeLabel"
                } else {
                    modeTitle
                }
                trigger?.setOnClickListener { v ->
                    showGameOptionsPopup(v, pkg)
                }
            }
        }.apply {
            key = game.packageName
            title = info?.loadLabel(pm) ?: game.packageName
            summary = when {
                !installed -> context.getString(R.string.library_game_unavailable)
                else -> context.describeGameMode(game.mode)
            }
            icon = info?.loadIcon(pm)
            layoutResource = R.layout.library_item
            isPersistent = false
            isEnabled = true
            // Launchability resolve luc tap — refreshInPlace khong cap nhat canLaunch stale.
            setOnPreferenceClickListener {
                launchGame(pkg)
                true
            }
        }
    }

    fun updateAppList() {
        apps.clear()
        systemSettings.userGames?.let { apps.addAll(it) }

        val sortedKeys = LibraryListOrder.sortGameKeys(apps.map { it.packageName }) { pkg ->
            getAppInfo(pkg)?.loadLabel(context.packageManager)?.toString() ?: pkg
        }
        val snap = LibraryListOrder.snapshot(sortedKeys)
        val desiredKeys = LibraryListOrder.keysOf(snap)

        // Cung snapshot keys → khong removeAll (tranh move anim / churn).
        if (LibraryListOrder.sameKeyOrder(lastKeySnapshot, desiredKeys) &&
            preferenceCount == desiredKeys.size
        ) {
            refreshInPlace(snap)
            return
        }

        rebuildFromSnapshot(snap, desiredKeys)
    }

    private fun refreshInPlace(snap: List<LibraryListOrder.Entry>) {
        currentTotalCount = snap.size
        for (entry in snap) {
            when (entry.key) {
                KEY_ADD_GAME -> {
                    currentAddPrefPosition = entry.position
                    makeAddPref.order = entry.order
                    // refreshChrome() goi notifyChanged trong subclass — khong goi protected tu ngoai.
                    makeAddPref.refreshChrome()
                }
                KEY_EMPTY_STATE -> {
                    currentEmptyPrefPosition = entry.position
                    emptyPref.order = entry.order
                    emptyPref.refreshChrome()
                }
                else -> {
                    val game = apps.firstOrNull { it.packageName == entry.key } ?: continue
                    val pref = findPreference<Preference>(entry.key) ?: continue
                    // title/icon/summary; click da resolve launch luc tap (khong stale canLaunch).
                    applyGameContent(pref, game)
                    pref.order = entry.order
                }
            }
        }
    }

    private fun rebuildFromSnapshot(
        snap: List<LibraryListOrder.Entry>,
        desiredKeys: List<String>,
    ) {
        removeAll()
        currentTotalCount = snap.size
        for (entry in snap) {
            when (entry.key) {
                KEY_ADD_GAME -> {
                    currentAddPrefPosition = entry.position
                    makeAddPref.order = entry.order
                    addPreference(makeAddPref)
                }
                KEY_EMPTY_STATE -> {
                    currentEmptyPrefPosition = entry.position
                    emptyPref.order = entry.order
                    addPreference(emptyPref)
                }
                else -> {
                    val game = apps.firstOrNull { it.packageName == entry.key } ?: continue
                    val pref = buildGamePref(game, entry.position, entry.total)
                    pref.order = entry.order
                    addPreference(pref)
                }
            }
        }
        lastKeySnapshot = desiredKeys
    }

    private fun applyGameContent(pref: Preference, game: UserGame) {
        val info = getAppInfo(game.packageName)
        val pm = context.packageManager
        val installed = info != null
        pref.title = info?.loadLabel(pm) ?: game.packageName
        pref.summary = when {
            !installed -> context.getString(R.string.library_game_unavailable)
            else -> context.describeGameMode(game.mode)
        }
        pref.icon = info?.loadIcon(pm)
    }

    private fun registerApp(packageName: String) {
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
        gameModeUtils.setAngleDriverChoice(packageName, GameModeUtils.DRIVER_CHOICE_DEFAULT)

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

    override fun onDetached() {
        activePopup?.forceCleanup()
        activePopup = null
        super.onDetached()
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        return true
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
