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
package io.chaldeaprjkt.gamespace.preferences.appselector

import java.text.Normalizer
import java.util.Locale

/**
 * Pure filter helpers cho man Add-game — host-testable, khong PackageManager.
 *
 * Query: trim; fold dau (NFD + bo combining + d/D -> d); match label HOAC package;
 * empty query -> giu nguyen cache.
 */
object AppSelectorFilter {

    data class Entry(
        val packageName: String,
        val label: String,
    )

    fun normalizeQuery(raw: String?): String = raw?.trim().orEmpty()

    /**
     * Fold tim kiem locale-stable (Locale.ROOT):
     * 1) map d/D/D -> d
     * 2) NFD
     * 3) bo NON_SPACING_MARK
     * 4) lowercase ROOT
     *
     * Vi du: "di" khop "Dich"; "d" khop "D".
     */
    fun foldForSearch(raw: String): String {
        if (raw.isEmpty()) return raw
        val mapped = buildString(raw.length) {
            for (ch in raw) {
                append(
                    when (ch) {
                        'đ', 'Đ', 'Ð' -> 'd'
                        else -> ch
                    },
                )
            }
        }
        val nfd = Normalizer.normalize(mapped, Normalizer.Form.NFD)
        val stripped = buildString(nfd.length) {
            for (ch in nfd) {
                val type = Character.getType(ch)
                if (type != Character.NON_SPACING_MARK.toInt() &&
                    type != Character.COMBINING_SPACING_MARK.toInt() &&
                    type != Character.ENCLOSING_MARK.toInt()
                ) {
                    append(ch)
                }
            }
        }
        return stripped.lowercase(Locale.ROOT)
    }

    fun isExcluded(
        packageName: String,
        ownPackage: String,
        registered: Set<String>,
    ): Boolean =
        packageName == ownPackage || packageName in registered

    fun matches(label: String, packageName: String, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return true
        val q = foldForSearch(normalizedQuery)
        if (q.isEmpty()) return true
        return foldForSearch(label).contains(q) ||
            foldForSearch(packageName).contains(q)
    }

    fun filterCached(entries: List<Entry>, rawQuery: String?): List<Entry> {
        val q = normalizeQuery(rawQuery)
        if (q.isEmpty()) return entries
        return entries.filter { matches(it.label, it.packageName, q) }
    }

    /**
     * Dedup theo packageName (giu lan gap dau), sort theo label fold (locale-stable).
     */
    fun distinctSortedByLabel(entries: List<Entry>): List<Entry> {
        val seen = HashSet<String>()
        val unique = ArrayList<Entry>(entries.size)
        for (e in entries) {
            if (seen.add(e.packageName)) unique.add(e)
        }
        return unique.sortedBy { foldForSearch(it.label) }
    }

    fun keysOf(entries: List<Entry>): List<String> = entries.map { it.packageName }
}
