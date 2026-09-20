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
package io.chaldeaprjkt.gamespace.data

import android.app.AxSandboxManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An icon khoi launcher (home + drawer) qua AxSandbox
 * setPackageHiddenFromLauncher — app van chay binh thuong.
 */
@Singleton
class GameIconVault @Inject constructor(
    private val context: Context,
    private val appSettings: AppSettings,
    private val systemSettings: SystemSettings,
) {

    fun isAvailable(): Boolean {
        if (!isPackageInstalled(SANDBOX_PACKAGE)) return false
        return try {
            context.getSystemService(AxSandboxManager::class.java) != null
        } catch (_: Throwable) {
            false
        }
    }

    var enabled: Boolean
        get() = appSettings.iconVault
        set(value) {
            appSettings.iconVault = value
            applyForLibrary(value)
        }

    fun onGameRegistered(packageName: String) {
        if (enabled) setLauncherHidden(packageName, true)
    }

    fun onGameUnregistered(packageName: String) {
        setLauncherHidden(packageName, false)
    }

    fun applyForLibrary(hide: Boolean) {
        systemSettings.userGames.forEach { setLauncherHidden(it.packageName, hide) }
    }

    private fun setLauncherHidden(packageName: String, hidden: Boolean) {
        if (packageName in BLACKLIST) return
        val mgr = try {
            context.getSystemService(AxSandboxManager::class.java)
        } catch (t: Throwable) {
            Log.w(TAG, "AxSandboxManager unavailable", t)
            null
        } ?: return
        try {
            mgr.setPackageHiddenFromLauncher(packageName, hidden)
        } catch (t: Throwable) {
            Log.w(TAG, "setPackageHiddenFromLauncher failed for $packageName", t)
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    companion object {
        private const val TAG = "GameIconVault"
        const val SANDBOX_PACKAGE = "com.android.axion.sandbox"

        private val BLACKLIST = setOf(
            "android",
            SANDBOX_PACKAGE,
            "com.android.settings",
            "io.chaldeaprjkt.gamespace",
        )
    }
}
