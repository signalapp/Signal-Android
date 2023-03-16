package org.thoughtcrime.securesms.calls.log

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.Observables
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.new.NewCallActivity
import org.thoughtcrime.securesms.components.Material3SearchToolbar
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.notifications.manual.NotificationProfileSelectionFragment
import org.thoughtcrime.securesms.conversationlist.ConversationFilterBehavior
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterSource
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationListFilterPullView.OnCloseClicked
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationListFilterPullView.OnFilterStateChanged
import org.thoughtcrime.securesms.conversationlist.chatfilter.FilterLerp
import org.thoughtcrime.securesms.conversationlist.chatfilter.FilterPullState
import org.thoughtcrime.securesms.databinding.CallLogFragmentBinding
import org.thoughtcrime.securesms.main.Material3OnScrollHelperBinder
import org.thoughtcrime.securesms.main.SearchBinder
import org.thoughtcrime.securesms.stories.tabs.ConversationListTab
import org.thoughtcrime.securesms.stories.tabs.ConversationListTabsViewModel
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.fragments.requireListener
import java.util.Objects

/**
 * Call Log tab.
 */
@SuppressLint("DiscouragedApi")
class CallLogFragment : Fragment(R.layout.call_log_fragment), CallLogAdapter.Callbacks, CallLogContextMenu.Callbacks {

  companion object {
    private const val LIST_SMOOTH_SCROLL_TO_TOP_THRESHOLD = 25
  }

  private val viewModel: CallLogViewModel by viewModels()
  private val binding: CallLogFragmentBinding by ViewBinderDelegate(CallLogFragmentBinding::bind)
  private val disposables = LifecycleDisposable()
  private val callLogContextMenu = CallLogContextMenu(this, this)
  private val callLogActionMode = CallLogActionMode(
    fragment = this,
    onResetSelectionState = {
      viewModel.clearSelected()
    }
  )

  private val tabsViewModel: ConversationListTabsViewModel by viewModels(ownerProducer = { requireActivity() })

  private val menuProvider = object : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
      menuInflater.inflate(R.menu.calls_tab_menu, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
      val isFiltered = viewModel.filterSnapshot == CallLogFilter.MISSED
      menu.findItem(R.id.action_clear_missed_call_filter).isVisible = isFiltered
      menu.findItem(R.id.action_filter_missed_calls).isVisible = !isFiltered
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
      when (menuItem.itemId) {
        R.id.action_settings -> startActivity(AppSettingsActivity.home(requireContext()))
        R.id.action_notification_profile -> NotificationProfileSelectionFragment.show(parentFragmentManager)
        R.id.action_filter_missed_calls -> filterMissedCalls()
        R.id.action_clear_missed_call_filter -> onClearFilterClicked()
      }

      return true
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner)

    val adapter = CallLogAdapter(this)
    disposables.bindTo(viewLifecycleOwner)
    disposables += viewModel.controller
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe {
        adapter.setPagingController(it)
      }

    disposables += Observables.combineLatest(viewModel.data, viewModel.selected)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (data, selected) ->
        adapter.submitCallRows(data, selected)
      }

    disposables += viewModel.selected
      .observeOn(AndroidSchedulers.mainThread())
      .distinctUntilChanged()
      .subscribe {
        if (!it.isNotEmpty(adapter.itemCount)) {
          callLogActionMode.end()
        } else {
          callLogActionMode.setCount(it.count(adapter.itemCount))
        }
      }

    binding.recycler.adapter = adapter
    requireListener<Material3OnScrollHelperBinder>().bindScrollHelper(binding.recycler)
    binding.fab.setOnClickListener {
      startActivity(NewCallActivity.createIntent(requireContext()))
    }

    initializePullToFilter()
    initializeTapToScrollToTop()

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (!closeSearchIfOpen()) {
            tabsViewModel.onChatsSelected()
          }
        }
      }
    )
  }

  override fun onResume() {
    super.onResume()
    initializeSearchAction()
  }

  private fun initializeTapToScrollToTop() {
    disposables += tabsViewModel.tabClickEvents
      .filter { it == ConversationListTab.CALLS }
      .subscribeBy(onNext = {
        val layoutManager = binding.recycler.layoutManager as? LinearLayoutManager ?: return@subscribeBy
        if (layoutManager.findFirstVisibleItemPosition() <= LIST_SMOOTH_SCROLL_TO_TOP_THRESHOLD) {
          binding.recycler.smoothScrollToPosition(0)
        } else {
          binding.recycler.scrollToPosition(0)
        }
      })
  }

  private fun initializeSearchAction() {
    val searchBinder = requireListener<SearchBinder>()
    searchBinder.getSearchAction().setOnClickListener {
      searchBinder.onSearchOpened()
      searchBinder.getSearchToolbar().get().setSearchInputHint(R.string.SearchToolbar_search)

      searchBinder.getSearchToolbar().get().listener = object : Material3SearchToolbar.Listener {
        override fun onSearchTextChange(text: String) {
          viewModel.setSearchQuery(text.trim())
        }

        override fun onSearchClosed() {
          viewModel.setSearchQuery("")
          searchBinder.onSearchClosed()
        }
      }
    }
  }

  private fun initializePullToFilter() {
    val collapsingToolbarLayout = binding.collapsingToolbar
    val openHeight = DimensionUnit.DP.toPixels(FilterLerp.FILTER_OPEN_HEIGHT).toInt()

    binding.pullView.onFilterStateChanged = OnFilterStateChanged { state: FilterPullState?, source: ConversationFilterSource ->
      when (state) {
        FilterPullState.CLOSING -> viewModel.setFilter(CallLogFilter.ALL)
        FilterPullState.OPENING -> {
          ViewUtil.setMinimumHeight(collapsingToolbarLayout, openHeight)
          viewModel.setFilter(CallLogFilter.MISSED)
        }

        FilterPullState.OPEN_APEX -> if (source === ConversationFilterSource.DRAG) {
          // TODO[alex] -- hint here? SignalStore.uiHints().incrementNeverDisplayPullToFilterTip()
        }

        FilterPullState.CLOSE_APEX -> ViewUtil.setMinimumHeight(collapsingToolbarLayout, 0)
        else -> Unit
      }
    }

    binding.pullView.onCloseClicked = OnCloseClicked {
      onClearFilterClicked()
    }

    val conversationFilterBehavior = Objects.requireNonNull<ConversationFilterBehavior?>((binding.recyclerCoordinatorAppBar.layoutParams as CoordinatorLayout.LayoutParams).behavior as ConversationFilterBehavior?)
    conversationFilterBehavior.callback = object : ConversationFilterBehavior.Callback {
      override fun onStopNestedScroll() {
        binding.pullView.onUserDragFinished()
      }

      override fun canStartNestedScroll(): Boolean {
        return !isSearchOpen() || binding.pullView.isCloseable()
      }
    }

    binding.recyclerCoordinatorAppBar.addOnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
      val progress = 1 - verticalOffset.toFloat() / -layout.height
      binding.pullView.onUserDrag(progress)
    }
  }

  override fun onCallClicked(callLogRow: CallLogRow.Call) {
    if (viewModel.selectionStateSnapshot.isNotEmpty(binding.recycler.adapter!!.itemCount)) {
      viewModel.toggleSelected(callLogRow.id)
    }
  }

  override fun onCallLongClicked(itemView: View, callLogRow: CallLogRow.Call): Boolean {
    callLogContextMenu.show(itemView, callLogRow)
    return true
  }

  override fun onClearFilterClicked() {
    binding.pullView.toggle()
    binding.recyclerCoordinatorAppBar.setExpanded(false, true)
  }

  override fun startSelection(call: CallLogRow.Call) {
    callLogActionMode.start()
    viewModel.toggleSelected(call.id)
  }

  private fun filterMissedCalls() {
    binding.pullView.toggle()
    binding.recyclerCoordinatorAppBar.setExpanded(false, true)
  }

  private fun isSearchOpen(): Boolean {
    return isSearchVisible() || viewModel.hasSearchQuery
  }

  private fun closeSearchIfOpen(): Boolean {
    if (isSearchOpen()) {
      requireListener<SearchBinder>().getSearchToolbar().get().collapse()
      requireListener<SearchBinder>().onSearchClosed()
      return true
    }
    return false
  }

  private fun isSearchVisible(): Boolean {
    return requireListener<SearchBinder>().getSearchToolbar().resolved() &&
      requireListener<SearchBinder>().getSearchToolbar().get().getVisibility() == View.VISIBLE
  }

  interface Callback {
    fun onMultiSelectStarted()
    fun onMultiSelectFinished()
  }
}
