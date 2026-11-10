/**
 * Host check — AppSelectorFilter (accent fold + package/label).
 */
package io.chaldeaprjkt.gamespace.tools

import io.chaldeaprjkt.gamespace.preferences.appselector.AppSelectorFilter
import io.chaldeaprjkt.gamespace.preferences.appselector.AppSelectorFilter.Entry
import kotlin.system.exitProcess

object HostAppSelectorFilterCheck {
    private val fails = ArrayList<String>()
    private fun check(cond: Boolean, msg: String) {
        if (!cond) fails.add(msg)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val cache = listOf(
            Entry("com.a.game", "Alpha Game"),
            Entry("com.b.puzzle", "Beta Puzzle"),
            Entry("org.chrome", "Chrome"),
            Entry("com.example.TEST", "MiXeD CaSe"),
            Entry("com.viet.translate", "Dịch"),
            Entry("com.viet.dong", "Đồng hồ"),
            Entry("com.accent.decomp", "Cafe\u0301"), // e + combining acute = Café NFD
        )

        check(
            AppSelectorFilter.filterCached(cache, "  ") == cache,
            "whitespace-only query restores all",
        )
        check(
            AppSelectorFilter.filterCached(cache, null) == cache,
            "null query restores all",
        )

        check(
            AppSelectorFilter.filterCached(cache, "alpha").map { it.packageName } ==
                listOf("com.a.game"),
            "label match",
        )
        check(
            AppSelectorFilter.filterCached(cache, "ORG.CHROME").map { it.packageName } ==
                listOf("org.chrome"),
            "package match ignoreCase",
        )
        check(
            AppSelectorFilter.filterCached(cache, "mixed").single().packageName ==
                "com.example.TEST",
            "mixed label case",
        )
        check(AppSelectorFilter.filterCached(cache, "zzz_none").isEmpty(), "no match empty")
        check(
            AppSelectorFilter.filterCached(cache, "  puzzle  ").single().packageName ==
                "com.b.puzzle",
            "trim query",
        )

        // Accent fold: di → Dịch; d → Đ
        check(
            AppSelectorFilter.foldForSearch("Dịch") == "dich",
            "fold Dịch → dich",
        )
        check(
            AppSelectorFilter.filterCached(cache, "di").any { it.label == "Dịch" },
            "di → Dịch",
        )
        check(
            AppSelectorFilter.filterCached(cache, "d").any { it.label == "Đồng hồ" },
            "d → Đ",
        )
        check(
            AppSelectorFilter.filterCached(cache, "dong").any { it.label == "Đồng hồ" },
            "dong → Đồng",
        )
        // Decomposed accent (NFD Café)
        check(
            AppSelectorFilter.filterCached(cache, "cafe").any { it.packageName == "com.accent.decomp" },
            "decomposed accent cafe → Café",
        )
        check(
            AppSelectorFilter.foldForSearch("Đ") == "d",
            "fold Đ → d",
        )

        check(
            AppSelectorFilter.isExcluded("com.me", "com.me", emptySet()),
            "exclude own package",
        )
        check(
            AppSelectorFilter.isExcluded("com.reg", "com.me", setOf("com.reg")),
            "exclude registered",
        )
        check(
            !AppSelectorFilter.isExcluded("com.ok", "com.me", setOf("com.reg")),
            "keep unregistered other",
        )

        val dupes = listOf(
            Entry("com.z", "Zebra"),
            Entry("com.a", "Apple"),
            Entry("com.z", "Zebra Dup"),
            Entry("com.m", "Mango"),
        )
        val sorted = AppSelectorFilter.distinctSortedByLabel(dupes)
        check(sorted.map { it.packageName } == listOf("com.a", "com.m", "com.z"), "distinct+sort")
        check(sorted.single { it.packageName == "com.z" }.label == "Zebra", "keep first duplicate")

        check(AppSelectorFilter.normalizeQuery("  Hi ") == "Hi", "normalize trim")
        check(AppSelectorFilter.normalizeQuery(null).isEmpty(), "normalize null")
        check(AppSelectorFilter.matches("Foo", "com.foo", ""), "empty query matches all")
        check(AppSelectorFilter.matches("Foo", "com.bar", "bar"), "pkg substring")
        check(!AppSelectorFilter.matches("Foo", "com.bar", "baz"), "no substring")

        if (fails.isNotEmpty()) {
            println("FAIL HostAppSelectorFilterCheck")
            fails.forEach { println(" - $it") }
            exitProcess(1)
        }
        println("PASS HostAppSelectorFilterCheck (accent fold di→Dịch, d→Đ, NFD, pkg)")
    }
}
