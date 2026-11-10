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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.LruCache
import io.chaldeaprjkt.gamespace.preferences.appselector.AppSelectorFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository quan ly danh sach ung dung va cache icon theo nhu cau cho Add Game.
 * Tranh goi IPC getApplicationInfo lap lai nhieu lan va decode icon toan bo truoc hoat hoa.
 */
@Singleton
class AppSelectorRepository @Inject constructor(
    private val context: Context,
    private val systemSettings: SystemSettings,
) {
    /** Cache danh sach tat ca ung dung launcher tren may. */
    @Volatile
    private var cachedLauncherApps: List<AppSelectorFilter.Entry>? = null

    /** Cache icon gioi han dung luong (LRU). */
    private val iconCache = LruCache<String, Drawable>(150)

    /**
     * Lay danh sach ung dung hop le cho Add Game tren Dispatchers.IO.
     * Neu cache hop le, chi loc theo userGames hien tai (nhanh < 1ms).
     */
    suspend fun getAvailableApps(forceRefresh: Boolean = false): List<AppSelectorFilter.Entry> =
        withContext(Dispatchers.IO) {
            val registered = systemSettings.userGames.map { it.packageName }.toSet()
            val ownPkg = context.packageName
            val all = if (cachedLauncherApps == null || forceRefresh) {
                queryLauncherApps()
            } else {
                cachedLauncherApps.orEmpty()
            }
            all.filterNot { AppSelectorFilter.isExcluded(it.packageName, ownPkg, registered) }
        }

    /**
     * Truy van PackageManager mot lan va luu vao bo nho.
     * Tan dung ResolveInfo.activityInfo.applicationInfo, khong goi them IPC getApplicationInfo.
     */
    private fun queryLauncherApps(): List<AppSelectorFilter.Entry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val raw = pm.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(0),
        ).mapNotNull { ri: ResolveInfo ->
            val info = ri.activityInfo ?: return@mapNotNull null
            val pkg = info.packageName
            if (pkg.isNullOrEmpty()) return@mapNotNull null
            val label = ri.loadLabel(pm)?.toString()
                ?: info.applicationInfo?.loadLabel(pm)?.toString()
                ?: pkg
            AppSelectorFilter.Entry(pkg, label)
        }
        val sorted = AppSelectorFilter.distinctSortedByLabel(raw)
        cachedLauncherApps = sorted
        return sorted
    }

    /**
     * Tai icon theo nhu cau (on-demand) va luu vao LruCache.
     */
    suspend fun loadIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        iconCache.get(packageName)?.let { return@withContext it }
        try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(packageName)
            iconCache.put(packageName, icon)
            icon
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Xoa cache khi co ung dung cai dat / go bo hoac thay doi.
     */
    fun invalidateCache() {
        cachedLauncherApps = null
        iconCache.evictAll()
    }
}
