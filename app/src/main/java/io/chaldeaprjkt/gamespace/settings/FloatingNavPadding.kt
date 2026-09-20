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

import androidx.preference.PreferenceFragmentCompat
import io.chaldeaprjkt.gamespace.R
import java.util.concurrent.CopyOnWriteArrayList

/** Chieu cao Compose floating nav — dung de padding preference list. */
object FloatingNavPaddingStore {
    @Volatile
    var heightPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            listeners.forEach { it.invoke() }
        }

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}

/** Padding day cho floating bottom nav — tranh che preference cuoi. */
internal fun PreferenceFragmentCompat.applyFloatingBottomNavPadding() {
    val list = listView ?: return
    val measured = FloatingNavPaddingStore.heightPx
    val fallback = resources.getDimensionPixelSize(R.dimen.gamespace_bottom_nav_content_padding)
    val extra = if (measured > 0) measured else fallback
    // Replace bottom padding rather than stacking on every resume.
    list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, extra)
    list.clipToPadding = false
}
