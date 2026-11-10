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
package io.chaldeaprjkt.gamespace.preferences

/**
 * Thu tu on dinh Library: Add luon FIRST, game sort label ben duoi.
 * Pure — host test empty/refresh/label invariance.
 */
object LibraryListOrder {
    /** Khop AppListPreferences.KEY_* — khong import Android Preference. */
    const val KEY_ADD = "add_game"
    const val KEY_EMPTY = "empty_state"

    /** order Preference: Add = 0; empty/games bat dau 1. */
    const val ORDER_ADD = 0
    const val ORDER_CONTENT_BASE = 1

    data class Entry(
        val key: String,
        val order: Int,
        /** Vi tri ve Top/Center/Bottom trong nhom (0..total-1). */
        val position: Int,
        val total: Int,
    )

    /**
     * @param gameKeysSorted packageName da sort theo label (caller sort).
     * @return snapshot keys+order+bg position; Add luon index 0.
     */
    fun snapshot(gameKeysSorted: List<String>): List<Entry> {
        val games = gameKeysSorted.filter { it.isNotEmpty() && it != KEY_ADD && it != KEY_EMPTY }
        return if (games.isEmpty()) {
            val total = 2
            listOf(
                Entry(KEY_ADD, ORDER_ADD, position = 0, total = total),
                Entry(KEY_EMPTY, ORDER_CONTENT_BASE, position = 1, total = total),
            )
        } else {
            val total = games.size + 1
            buildList(total) {
                add(Entry(KEY_ADD, ORDER_ADD, position = 0, total = total))
                games.forEachIndexed { i, key ->
                    add(
                        Entry(
                            key = key,
                            order = ORDER_CONTENT_BASE + i,
                            position = i + 1,
                            total = total,
                        ),
                    )
                }
            }
        }
    }

    fun keysOf(snapshot: List<Entry>): List<String> = snapshot.map { it.key }

    /** Sort game theo label; missing label → packageName. */
    fun sortGameKeys(
        packageNames: List<String>,
        labelOf: (String) -> String,
    ): List<String> =
        packageNames
            .distinct()
            .sortedWith(
                compareBy(
                    { labelOf(it).lowercase() },
                    { it.lowercase() },
                ),
            )

    /**
     * Bg Top/Center/Bottom/Single — trung AppListPreferences.getBackgroundForPosition.
     */
    fun backgroundKind(position: Int, total: Int): String = when {
        total <= 1 -> "single"
        position == 0 -> "top"
        position == total - 1 -> "bottom"
        else -> "center"
    }

    fun sameKeyOrder(a: List<String>, b: List<String>): Boolean =
        a.size == b.size && a.indices.all { a[it] == b[it] }
}
