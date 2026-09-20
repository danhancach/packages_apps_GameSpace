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
package io.chaldeaprjkt.gamespace.gamebar.tiles

/**
 * Phan loai tile tren GameBar panel.
 * GAME = dieu khien phien choi; SYSTEM = toggle he thong (mini QS).
 */
enum class TileCategory {
    GAME,
    SYSTEM,
}

object TileCategories {
    /** Tile id thuoc nhom Game (con lai = System). */
    private val GAME_IDS = setOf(
        "notification",
        "stay_awake",
        "fps_info",
        "boost_memory",
        "touch_boost",
        "hspc",
        "high_touch",
        "screen_record",
        "screenshot",
        "caffeine",
    )

    /** Thu tu mac dinh hien tren tab Game (chi id ton tai moi dung). */
    val DEFAULT_GAME_ORDER = listOf(
        "fps_info",
        "hspc",
        "high_touch",
        "boost_memory",
        "notification",
        "stay_awake",
        "touch_boost",
        "screenshot",
        "screen_record",
        "caffeine",
    )

    /** Thu tu mac dinh hien tren tab System — it tile, mot trang. */
    val DEFAULT_SYSTEM_ORDER = listOf(
        "wifi",
        "bluetooth",
        "zen",
        "rotation",
        "mobile_data",
        "flashlight",
    )

    fun categoryOf(tileId: String): TileCategory =
        if (tileId in GAME_IDS) TileCategory.GAME else TileCategory.SYSTEM

    fun isGame(tileId: String): Boolean = tileId in GAME_IDS
}
