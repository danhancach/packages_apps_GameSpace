/*
 * Copyright (C) 2026 crDroid Android Project
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSelectorRepository
import io.chaldeaprjkt.gamespace.preferences.AppListPreferences
import io.chaldeaprjkt.gamespace.settings.SettingsActivity
import io.chaldeaprjkt.gamespace.ui.overscroll.MiuixPreferenceScrollCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Man Add-game (live): fragment_app_selector + CollapsingToolbar + search pill + RecyclerView ListAdapter.
 * Dung AppSelectorRepository cache ung dung trong bo nho va load icon on-demand.
 */
@AndroidEntryPoint(Fragment::class)
class AppSelectorFragment : Hilt_AppSelectorFragment() {

    @Inject
    lateinit var repository: AppSelectorRepository

    private var searchInput: EditText? = null
    private var clearButton: ImageButton? = null
    private var recyclerView: RecyclerView? = null
    private var emptyView: TextView? = null
    private var loadingView: ProgressBar? = null
    private var adapter: AppSelectorAdapter? = null

    private var allApps: List<AppSelectorFilter.Entry> = emptyList()
    private var currentQuery: String = ""
    private var suppressWatcher: Boolean = false

    private val backWhileSearching = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val input = searchInput ?: run {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                return
            }
            when {
                input.text?.isNotEmpty() == true -> input.text?.clear()
                input.hasFocus() -> {
                    input.clearFocus()
                    hideIme(input)
                    isEnabled = false
                }
                else -> {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    private val queryWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (suppressWatcher) return
            currentQuery = s?.toString().orEmpty()
            updateClearVisibility()
            updateBackCallbackEnabled()
            filterAndSubmitList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentQuery = savedInstanceState?.getString(STATE_QUERY).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val root = inflater.inflate(R.layout.fragment_app_selector, container, false)

        val collapsingToolbar = root.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)
        collapsingToolbar.title = getString(R.string.app_selector_label)

        val appBar = root.findViewById<AppBarLayout>(R.id.app_bar)
        // Triet tieu stateListAnimator mac dinh; khoa elevation 8dp co dinh tren cung RV
        appBar.stateListAnimator = null
        val elevationPx = resources.getDimension(R.dimen.gamespace_appbar_elevation)
        ViewCompat.setElevation(appBar, elevationPx)
        ViewCompat.setTranslationZ(appBar, elevationPx)
        appBar.bringToFront()

        val toolbar = root.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        // Mui ten back tron Expressive (chuan AOSP SettingsLib)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description)
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        searchInput = root.findViewById(R.id.app_selector_search_input)
        clearButton = root.findViewById(R.id.app_selector_search_clear)
        recyclerView = root.findViewById(R.id.apps_list)
        emptyView = root.findViewById(R.id.apps_empty)
        loadingView = root.findViewById(R.id.apps_loading)

        wireSearch()
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val list = recyclerView ?: return
        list.layoutManager = LinearLayoutManager(requireContext())
        val listAdapter = AppSelectorAdapter(
            viewScope = viewLifecycleOwner.lifecycleScope,
            onAppClicked = { pkg -> deliverSelection(pkg) },
            loadIcon = { pkg -> repository.loadIcon(pkg) },
        )
        adapter = listAdapter
        list.adapter = listAdapter
        // Khe 2dp giua the — SettingsBasePreferenceFragment.MarginItemDecoration
        if (list.itemDecorationCount == 0) {
            list.addItemDecoration(AppSelectorCardGapDecoration())
        }
        MiuixPreferenceScrollCoordinator.attach(list)

        // Pad status bar neu dang embedded trong SettingsActivity; standalone AppSelectorActivity da co padding root
        (activity as? SettingsActivity)?.applyStatusBarInset(view)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backWhileSearching,
        )

        loadApps(initial = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_QUERY, currentQuery)
    }

    override fun onDestroyView() {
        searchInput?.removeTextChangedListener(queryWatcher)
        recyclerView?.let { MiuixPreferenceScrollCoordinator.detach(it) }
        recyclerView = null
        emptyView = null
        loadingView = null
        adapter = null
        searchInput = null
        clearButton = null
        super.onDestroyView()
    }

    fun onRouteClosing() {
        val input = searchInput ?: return
        input.clearFocus()
        hideIme(input)
        backWhileSearching.isEnabled = false
    }

    fun onRouteClosed() {
        onRouteClosing()
        // Lam moi danh sach khi dong de loai bo game vua duoc them
        refreshApps(force = false)
    }

    fun onRouteOpened() {
        refreshApps(force = false)
    }

    private fun wireSearch() {
        val input = searchInput ?: return
        val clear = clearButton ?: return
        suppressWatcher = true
        input.setText(currentQuery)
        input.setSelection(input.text?.length ?: 0)
        suppressWatcher = false
        updateClearVisibility()
        updateBackCallbackEnabled()

        input.addTextChangedListener(queryWatcher)
        input.setOnFocusChangeListener { _, hasFocus ->
            updateBackCallbackEnabled()
            if (!hasFocus) hideIme(input)
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideIme(input)
                true
            } else {
                false
            }
        }
        clear.setOnClickListener {
            input.text?.clear()
            input.requestFocus()
        }
    }

    private fun updateClearVisibility() {
        clearButton?.visibility =
            if (currentQuery.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun updateBackCallbackEnabled() {
        val input = searchInput
        backWhileSearching.isEnabled =
            input != null && (input.hasFocus() || input.text?.isNotEmpty() == true)
    }

    private fun hideIme(target: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(target.windowToken, 0)
    }

    private fun deliverSelection(packageName: String) {
        val host = activity as? SettingsActivity
        if (host != null && host.isAddGameRouteActive()) {
            host.consumeAddGameSelection(packageName)
            return
        }
        requireActivity().setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(AppListPreferences.EXTRA_APP, packageName)
            },
        )
        requireActivity().finish()
    }

    private fun loadApps(initial: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (allApps.isEmpty()) {
                loadingView?.visibility = View.VISIBLE
            }
            val apps = repository.getAvailableApps(forceRefresh = false)
            allApps = apps
            loadingView?.visibility = View.GONE
            filterAndSubmitList()
        }
    }

    private fun refreshApps(force: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = repository.getAvailableApps(forceRefresh = force)
            allApps = apps
            filterAndSubmitList()
        }
    }

    private fun filterAndSubmitList() {
        val filtered = AppSelectorFilter.filterCached(allApps, currentQuery)
        adapter?.submitList(filtered) {
            // Vi tri Top/Bottom doi khi loc — can bind lai nen the
            adapter?.notifyItemRangeChanged(0, filtered.size, AppSelectorAdapter.PAYLOAD_CARD)
            emptyView?.visibility =
                if (filtered.isEmpty() && allApps.isNotEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }
    }

    companion object {
        private const val STATE_QUERY = "app_selector_query"
    }
}

/** Gap doc 2dp giua the Expressive (khong dung layout_marginBottom). */
private class AppSelectorCardGapDecoration : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val total = parent.adapter?.itemCount ?: return
        if (position < total - 1) {
            outRect.bottom = view.resources.getDimensionPixelSize(
                R.dimen.gamespace_preference_card_gap,
            )
        }
    }
}

private fun expressiveCardBackground(position: Int, total: Int): Int = when {
    total <= 1 -> R.drawable.gamespace_preference_bg_single
    position == 0 -> R.drawable.gamespace_preference_bg_top
    position == total - 1 -> R.drawable.gamespace_preference_bg_bottom
    else -> R.drawable.gamespace_preference_bg_center
}

/**
 * ListAdapter voi DiffUtil + nen the Top/Center/Bottom/Single chuan SettingsLib Expressive.
 */
private class AppSelectorAdapter(
    private val viewScope: CoroutineScope,
    private val onAppClicked: (String) -> Unit,
    private val loadIcon: suspend (String) -> Drawable?,
) : ListAdapter<AppSelectorFilter.Entry, AppSelectorAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.app_selector_item, parent, false)
        return ViewHolder(view, viewScope, onAppClicked, loadIcon)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_CARD)) {
            holder.bindCard(position, itemCount)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    class ViewHolder(
        itemView: View,
        private val viewScope: CoroutineScope,
        private val onAppClicked: (String) -> Unit,
        private val loadIcon: suspend (String) -> Drawable?,
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(android.R.id.title)
        private val summaryView: TextView = itemView.findViewById(android.R.id.summary)
        private val iconView: ImageView = itemView.findViewById(android.R.id.icon)
        private var loadJob: Job? = null

        fun bindCard(position: Int, total: Int) {
            itemView.setBackgroundResource(expressiveCardBackground(position, total))
        }

        fun bind(entry: AppSelectorFilter.Entry, position: Int, total: Int) {
            titleView.text = entry.label
            summaryView.text = entry.packageName
            bindCard(position, total)
            itemView.setOnClickListener { onAppClicked(entry.packageName) }

            loadJob?.cancel()
            iconView.setImageDrawable(null)
            loadJob = viewScope.launch {
                val icon = loadIcon(entry.packageName)
                iconView.setImageDrawable(icon)
            }
        }

        fun recycle() {
            loadJob?.cancel()
            loadJob = null
            iconView.setImageDrawable(null)
        }
    }

    companion object {
        const val PAYLOAD_CARD = "card_chrome"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppSelectorFilter.Entry>() {
            override fun areItemsTheSame(
                oldItem: AppSelectorFilter.Entry,
                newItem: AppSelectorFilter.Entry,
            ): Boolean = oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(
                oldItem: AppSelectorFilter.Entry,
                newItem: AppSelectorFilter.Entry,
            ): Boolean = oldItem.label == newItem.label
        }
    }
}
