package org.thoughtcrime.securesms.calls.log

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.Flowables
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.DimensionUnit
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.new.NewCallActivity
import org.thoughtcrime.securesms.components.Material3SearchToolbar
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.notifications.manual.NotificationProfileSelectionFragment
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsActivity
import org.thoughtcrime.securesms.conversation.SignalBottomActionBarController
import org.thoughtcrime.securesms.conversationlist.ConversationFilterBehavior
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterSource
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationListFilterPullView.OnCloseClicked
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationListFilterPullView.OnFilterStateChanged
import org.thoughtcrime.securesms.conversationlist.chatfilter.FilterLerp
import org.thoughtcrime.securesms.conversationlist.chatfilter.FilterPullState
import org.thoughtcrime.securesms.databinding.CallLogFragmentBinding
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.main.Material3OnScrollHelperBinder
import org.thoughtcrime.securesms.main.SearchBinder
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.tabs.ConversationListTab
import org.thoughtcrime.securesms.stories.tabs.ConversationListTabsViewModel
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SnapToTopDataObserver
import org.thoughtcrime.securesms.util.SnapToTopDataObserver.ScrollRequestValidator
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.visible
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
  private val callLogActionMode = CallLogActionMode(CallLogActionModeCallback())

  private lateinit var signalBottomActionBarController: SignalBottomActionBarController

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
    adapter.setPagingController(viewModel.controller)

    val snapToTopDataObserver = SnapToTopDataObserver(
      binding.recycler,
      object : ScrollRequestValidator {
        override fun isPositionStillValid(position: Int): Boolean {
          return position < adapter.itemCount && position >= 0
        }

        override fun isItemAtPositionLoaded(position: Int): Boolean {
          return adapter.getItem(position) != null
        }
      }
    ) {
      val layoutManager = binding.recycler.layoutManager as? LinearLayoutManager ?: return@SnapToTopDataObserver
      if (layoutManager.findFirstVisibleItemPosition() <= LIST_SMOOTH_SCROLL_TO_TOP_THRESHOLD) {
        binding.recycler.smoothScrollToPosition(0)
      } else {
        binding.recycler.scrollToPosition(0)
      }
    }

    disposables += Flowables.combineLatest(viewModel.data, viewModel.selectedAndStagedDeletion)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (data, selected) ->
        val filteredCount = adapter.submitCallRows(data, selected.first, selected.second)
        binding.emptyState.visible = filteredCount == 0
      }

    disposables += Flowables.combineLatest(viewModel.selectedAndStagedDeletion, viewModel.totalCount)
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (selected, totalCount) ->
        if (selected.first.isNotEmpty(totalCount)) {
          callLogActionMode.setCount(selected.first.count(totalCount))
        } else {
          callLogActionMode.end()
        }
      }

    binding.recycler.adapter = adapter

    requireListener<Material3OnScrollHelperBinder>().bindScrollHelper(binding.recycler)
    binding.fab.setOnClickListener {
      startActivity(NewCallActivity.createIntent(requireContext()))
    }

    binding.pullView.setPillText(R.string.CallLogFragment__filtered_by_missed)

    binding.bottomActionBar.setItems(
      listOf(
        ActionItem(
          iconRes = R.drawable.symbol_check_circle_24,
          title = getString(R.string.CallLogFragment__select_all)
        ) {
          viewModel.selectAll()
        },
        ActionItem(
          iconRes = R.drawable.symbol_trash_24,
          title = getString(R.string.CallLogFragment__delete),
          action = this::handleDeleteSelectedRows
        )
      )
    )

    initializePullToFilter()
    initializeTapToScrollToTop(snapToTopDataObserver)

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

    signalBottomActionBarController = SignalBottomActionBarController(
      binding.bottomActionBar,
      binding.recycler,
      BottomActionBarControllerCallback()
    )
  }

  override fun onResume() {
    super.onResume()
    initializeSearchAction()
    ApplicationDependencies.getDeletedCallEventManager().scheduleIfNecessary()
    viewModel.markAllCallEventsRead()
  }

  private fun initializeTapToScrollToTop(snapToTopDataObserver: SnapToTopDataObserver) {
    disposables += tabsViewModel.tabClickEvents
      .filter { it == ConversationListTab.CALLS }
      .subscribeBy(onNext = {
        snapToTopDataObserver.requestScrollPosition(0)
      })
  }

  private fun handleDeleteSelectedRows() {
    val count = callLogActionMode.getCount()
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(resources.getQuantityString(R.plurals.CallLogFragment__delete_d_calls, count, count))
      .setPositiveButton(R.string.CallLogFragment__delete_for_me) { _, _ ->
        viewModel.stageSelectionDeletion()
        callLogActionMode.end()
        Snackbar
          .make(
            binding.root,
            resources.getQuantityString(R.plurals.CallLogFragment__d_calls_deleted, count, count),
            Snackbar.LENGTH_SHORT
          )
          .addCallback(SnackbarDeletionCallback())
          .setAction(R.string.CallLogFragment__undo) {
            viewModel.cancelStagedDeletion()
          }
          .show()
      }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
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
        return !callLogActionMode.isInActionMode() || !isSearchOpen() || binding.pullView.isCloseable()
      }
    }

    binding.recyclerCoordinatorAppBar.addOnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
      val progress = 1 - verticalOffset.toFloat() / -layout.height
      binding.pullView.onUserDrag(progress)
    }
  }

  override fun onCreateACallLinkClicked() {
    findNavController().navigate(R.id.createCallLinkBottomSheet)
  }

  override fun onCallClicked(callLogRow: CallLogRow.Call) {
    if (viewModel.selectionStateSnapshot.isNotEmpty(binding.recycler.adapter!!.itemCount)) {
      viewModel.toggleSelected(callLogRow.id)
    } else {
      val intent = ConversationSettingsActivity.forCall(requireContext(), callLogRow.peer, (callLogRow.id as CallLogRow.Id.Call).children.toLongArray())
      startActivity(intent)
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

  override fun onStartAudioCallClicked(peer: Recipient) {
    CommunicationActions.startVoiceCall(this, peer)
  }

  override fun onStartVideoCallClicked(peer: Recipient) {
    CommunicationActions.startVideoCall(this, peer)
  }

  override fun startSelection(call: CallLogRow.Call) {
    callLogActionMode.start()
    viewModel.toggleSelected(call.id)
  }

  override fun deleteCall(call: CallLogRow.Call) {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(resources.getQuantityString(R.plurals.CallLogFragment__delete_d_calls, 1, 1))
      .setPositiveButton(R.string.CallLogFragment__delete_for_me) { _, _ ->
        viewModel.stageCallDeletion(call)
        Snackbar
          .make(
            binding.root,
            resources.getQuantityString(R.plurals.CallLogFragment__d_calls_deleted, 1, 1),
            Snackbar.LENGTH_SHORT
          )
          .addCallback(SnackbarDeletionCallback())
          .setAction(R.string.CallLogFragment__undo) {
            viewModel.cancelStagedDeletion()
          }
          .show()
      }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
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

  private inner class BottomActionBarControllerCallback : SignalBottomActionBarController.Callback {
    override fun onBottomActionBarVisibilityChanged(visibility: Int) = Unit
  }

  private inner class CallLogActionModeCallback : CallLogActionMode.Callback {
    override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
      val actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(callback)
      requireListener<Callback>().onMultiSelectStarted()
      signalBottomActionBarController.setVisibility(true)
      return actionMode
    }

    override fun onActionModeWillEnd() {
      requireListener<Callback>().onMultiSelectFinished()
      signalBottomActionBarController.setVisibility(false)
    }

    override fun getResources(): Resources = resources
    override fun onResetSelectionState() {
      viewModel.clearSelected()
    }
  }

  private inner class SnackbarDeletionCallback : Snackbar.Callback() {
    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
      viewModel.commitStagedDeletion()
    }
  }

  interface Callback {
    fun onMultiSelectStarted()
    fun onMultiSelectFinished()
  }
}
