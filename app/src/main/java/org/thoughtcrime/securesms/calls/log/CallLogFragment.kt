package org.thoughtcrime.securesms.calls.log

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.SharedElementCallback
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionInflater
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.Flowables
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.DimensionUnit
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsActivity
import org.thoughtcrime.securesms.calls.new.NewCallActivity
import org.thoughtcrime.securesms.components.Material3SearchToolbar
import org.thoughtcrime.securesms.components.ProgressCardDialogFragment
import org.thoughtcrime.securesms.components.ScrollToPositionDelegate
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.notifications.manual.NotificationProfileSelectionFragment
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsActivity
import org.thoughtcrime.securesms.conversation.ConversationUpdateTick
import org.thoughtcrime.securesms.conversation.SignalBottomActionBarController
import org.thoughtcrime.securesms.conversation.v2.ConversationDialogs
import org.thoughtcrime.securesms.conversationlist.ConversationFilterBehavior
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterSource
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationListFilterPullView.OnCloseClicked
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationListFilterPullView.OnFilterStateChanged
import org.thoughtcrime.securesms.conversationlist.chatfilter.FilterLerp
import org.thoughtcrime.securesms.conversationlist.chatfilter.FilterPullState
import org.thoughtcrime.securesms.databinding.CallLogFragmentBinding
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.main.Material3OnScrollHelperBinder
import org.thoughtcrime.securesms.main.SearchBinder
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.tabs.ConversationListTab
import org.thoughtcrime.securesms.stories.tabs.ConversationListTabsViewModel
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.doAfterNextLayout
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.visible
import java.util.Objects
import java.util.concurrent.TimeUnit

/**
 * Call Log tab.
 */
@SuppressLint("DiscouragedApi")
class CallLogFragment : Fragment(R.layout.call_log_fragment), CallLogAdapter.Callbacks, CallLogContextMenu.Callbacks {

  companion object {
    private val TAG = Log.tag(CallLogFragment::class.java)
  }

  private var filterViewOffsetChangeListener: AppBarLayout.OnOffsetChangedListener? = null

  private val viewModel: CallLogViewModel by activityViewModels()
  private val binding: CallLogFragmentBinding by ViewBinderDelegate(CallLogFragmentBinding::bind) {
    binding.recyclerCoordinatorAppBar.removeOnOffsetChangedListener(filterViewOffsetChangeListener)
  }

  private val disposables = LifecycleDisposable()
  private val callLogContextMenu = CallLogContextMenu(this, this)
  private val callLogActionMode = CallLogActionMode(CallLogActionModeCallback())
  private val conversationUpdateTick: ConversationUpdateTick = ConversationUpdateTick(this::onTimestampTick)
  private var callLogAdapter: CallLogAdapter? = null

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
      menu.findItem(R.id.action_clear_call_history).isVisible = !viewModel.isEmpty
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
      when (menuItem.itemId) {
        R.id.action_clear_call_history -> clearCallHistory()
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
    initializeSharedElementTransition()

    viewLifecycleOwner.lifecycle.addObserver(conversationUpdateTick)
    viewLifecycleOwner.lifecycle.addObserver(viewModel.callLogPeekHelper)

    val callLogAdapter = CallLogAdapter(this)
    disposables.bindTo(viewLifecycleOwner)
    callLogAdapter.setPagingController(viewModel.controller)
    callLogAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
      override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        (requireActivity() as? MainActivity)?.onFirstRender()
        callLogAdapter.unregisterAdapterDataObserver(this)
      }
    })

    val scrollToPositionDelegate = ScrollToPositionDelegate(
      recyclerView = binding.recycler,
      canJumpToPosition = { callLogAdapter.isAvailableAround(it) }
    )

    disposables += scrollToPositionDelegate
    disposables += Flowables.combineLatest(viewModel.data, viewModel.selected)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (data, selected) ->
        val filteredCount = callLogAdapter.submitCallRows(
          data,
          selected,
          viewModel.callLogPeekHelper.localDeviceCallRecipientId,
          scrollToPositionDelegate::notifyListCommitted
        )
        binding.emptyState.visible = filteredCount == 0
      }

    disposables += Flowables.combineLatest(viewModel.selected, viewModel.totalCount)
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (selected, totalCount) ->
        if (selected.isNotEmpty(totalCount)) {
          callLogActionMode.setCount(selected.count(totalCount))
        } else {
          callLogActionMode.end()
        }
      }

    binding.recycler.adapter = callLogAdapter
    this.callLogAdapter = callLogAdapter

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

    initializePullToFilter(scrollToPositionDelegate)
    initializeTapToScrollToTop(scrollToPositionDelegate)

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
    AppDependencies.deletedCallEventManager.scheduleIfNecessary()
    viewModel.markAllCallEventsRead()
  }

  private fun onTimestampTick() {
    callLogAdapter?.onTimestampTick()
  }

  private fun initializeSharedElementTransition() {
    ViewCompat.setTransitionName(binding.fab, "new_convo_fab")
    ViewCompat.setTransitionName(binding.fabSharedElementTarget, "camera_fab")

    sharedElementEnterTransition = TransitionInflater.from(requireContext()).inflateTransition(R.transition.change_transform_fabs)
    setEnterSharedElementCallback(object : SharedElementCallback() {
      override fun onSharedElementStart(sharedElementNames: MutableList<String>?, sharedElements: MutableList<View>?, sharedElementSnapshots: MutableList<View>?) {
        if (sharedElementNames?.contains("camera_fab") == true) {
          this@CallLogFragment.binding.fab.setImageResource(R.drawable.symbol_edit_24)
          disposables += Single.timer(200, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy {
              this@CallLogFragment.binding.fab.setImageResource(R.drawable.symbol_phone_plus_24)
              this@CallLogFragment.binding.fabSharedElementTarget.alpha = 0f
            }
        }
      }
    })
  }

  private fun initializeTapToScrollToTop(scrollToPositionDelegate: ScrollToPositionDelegate) {
    disposables += tabsViewModel.tabClickEvents
      .filter { it == ConversationListTab.CALLS }
      .subscribeBy(onNext = {
        scrollToPositionDelegate.resetScrollPosition()
      })
  }

  private fun handleDeleteSelectedRows() {
    val count = callLogActionMode.getCount()
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(resources.getQuantityString(R.plurals.CallLogFragment__delete_d_calls, count, count))
      .setMessage(getString(R.string.CallLogFragment__call_links_youve_created))
      .setPositiveButton(R.string.CallLogFragment__delete) { _, _ ->
        performDeletion(count, viewModel.stageSelectionDeletion())
        callLogActionMode.end()
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

  private fun initializePullToFilter(scrollToPositionDelegate: ScrollToPositionDelegate) {
    val collapsingToolbarLayout = binding.collapsingToolbar
    val openHeight = DimensionUnit.DP.toPixels(FilterLerp.FILTER_OPEN_HEIGHT).toInt()

    binding.pullView.onFilterStateChanged = OnFilterStateChanged { state: FilterPullState?, source: ConversationFilterSource ->
      when (state) {
        FilterPullState.CLOSING -> {
          viewModel.setFilter(CallLogFilter.ALL)
          binding.recycler.doAfterNextLayout {
            scrollToPositionDelegate.resetScrollPosition()
          }
        }

        FilterPullState.OPENING -> {
          ViewUtil.setMinimumHeight(collapsingToolbarLayout, openHeight)
          viewModel.setFilter(CallLogFilter.MISSED)
        }

        FilterPullState.OPEN_APEX -> if (source === ConversationFilterSource.DRAG) {
          // TODO[alex] -- hint here? SignalStore.uiHints.incrementNeverDisplayPullToFilterTip()
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
        return !callLogActionMode.isInActionMode() && !isSearchOpen()
      }
    }

    filterViewOffsetChangeListener = AppBarLayout.OnOffsetChangedListener {
        layout: AppBarLayout, verticalOffset: Int ->
      val progress = 1 - verticalOffset.toFloat() / -layout.height
      binding.pullView.onUserDrag(progress)
    }

    binding.recyclerCoordinatorAppBar.addOnOffsetChangedListener(filterViewOffsetChangeListener)

    if (viewModel.filterSnapshot != CallLogFilter.ALL) {
      binding.root.doAfterNextLayout {
        binding.pullView.openImmediate()
      }
    }
  }

  override fun onCreateACallLinkClicked() {
    findNavController().navigate(R.id.createCallLinkBottomSheet)
  }

  override fun onCallClicked(callLogRow: CallLogRow.Call) {
    if (viewModel.selectionStateSnapshot.isNotEmpty(binding.recycler.adapter!!.itemCount)) {
      viewModel.toggleSelected(callLogRow.id)
    } else if (!callLogRow.peer.isCallLink) {
      val intent = ConversationSettingsActivity.forCall(
        requireContext(),
        callLogRow.peer,
        (callLogRow.id as CallLogRow.Id.Call).children.toLongArray()
      )
      startActivity(intent)
    } else {
      startActivity(CallLinkDetailsActivity.createIntent(requireContext(), callLogRow.peer.requireCallLinkRoomId()))
    }
  }

  override fun onCallLinkClicked(callLogRow: CallLogRow.CallLink) {
    if (viewModel.selectionStateSnapshot.isNotEmpty(binding.recycler.adapter!!.itemCount)) {
      viewModel.toggleSelected(callLogRow.id)
    } else {
      startActivity(CallLinkDetailsActivity.createIntent(requireContext(), callLogRow.record.roomId))
    }
  }

  override fun onCallLongClicked(itemView: View, callLogRow: CallLogRow.Call): Boolean {
    callLogContextMenu.show(binding.recycler, itemView, callLogRow)
    return true
  }

  override fun onCallLinkLongClicked(itemView: View, callLinkLogRow: CallLogRow.CallLink): Boolean {
    callLogContextMenu.show(binding.recycler, itemView, callLinkLogRow)
    return true
  }

  override fun onClearFilterClicked() {
    binding.pullView.toggle()
    binding.recyclerCoordinatorAppBar.setExpanded(false, true)
  }

  override fun onStartAudioCallClicked(recipient: Recipient) {
    CommunicationActions.startVoiceCall(this, recipient) {
      YouAreAlreadyInACallSnackbar.show(requireView())
    }
  }

  override fun onStartVideoCallClicked(recipient: Recipient, canUserBeginCall: Boolean) {
    if (canUserBeginCall) {
      CommunicationActions.startVideoCall(this, recipient) {
        YouAreAlreadyInACallSnackbar.show(requireView())
      }
    } else {
      ConversationDialogs.displayCannotStartGroupCallDueToPermissionsDialog(requireContext())
    }
  }

  override fun startSelection(call: CallLogRow) {
    callLogActionMode.start()
    viewModel.toggleSelected(call.id)
  }

  override fun deleteCall(call: CallLogRow) {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(resources.getQuantityString(R.plurals.CallLogFragment__delete_d_calls, 1, 1))
      .setMessage(getString(R.string.CallLogFragment__call_links_youve_created))
      .setPositiveButton(R.string.CallLogFragment__delete) { _, _ ->
        performDeletion(1, viewModel.stageCallDeletion(call))
      }
      .show()
  }

  private fun filterMissedCalls() {
    binding.pullView.toggle()
    binding.recyclerCoordinatorAppBar.setExpanded(false, true)
  }

  private fun clearCallHistory() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.CallLogFragment__clear_call_history_question)
      .setMessage(R.string.CallLogFragment__this_will_permanently_delete_all_call_history)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        callLogActionMode.end()
        performDeletion(-1, viewModel.stageDeleteAll())
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
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
      requireListener<SearchBinder>().getSearchToolbar().get().visibility == View.VISIBLE
  }

  private fun performDeletion(count: Int, callLogStagedDeletion: CallLogStagedDeletion) {
    var progressDialog: ProgressCardDialogFragment? = null
    var errorDialog: AlertDialog? = null

    fun cleanUp() {
      progressDialog?.dismissAllowingStateLoss()
      progressDialog = null
      errorDialog?.dismiss()
      errorDialog = null
    }

    val snackbarMessage = if (count == -1) {
      getString(R.string.CallLogFragment__cleared_call_history)
    } else {
      resources.getQuantityString(R.plurals.CallLogFragment__d_calls_deleted, count, count)
    }

    viewModel.delete(callLogStagedDeletion)
      .observeOn(AndroidSchedulers.mainThread())
      .doOnSubscribe {
        progressDialog = ProgressCardDialogFragment.create(getString(R.string.CallLogFragment__deleting))
        progressDialog?.show(parentFragmentManager, null)
      }
      .doOnDispose { cleanUp() }
      .subscribeBy {
        cleanUp()
        when (it) {
          CallLogDeletionResult.Empty -> Unit
          is CallLogDeletionResult.FailedToRevoke -> {
            errorDialog = MaterialAlertDialogBuilder(requireContext())
              .setMessage(resources.getQuantityString(R.plurals.CallLogFragment__cant_delete_call_link, it.failedRevocations))
              .setPositiveButton(android.R.string.ok, null)
              .show()
          }
          CallLogDeletionResult.Success -> {
            Snackbar
              .make(
                binding.root,
                snackbarMessage,
                Snackbar.LENGTH_SHORT
              )
              .show()
          }
          is CallLogDeletionResult.UnknownFailure -> {
            Log.w(TAG, "Deletion failed.", it.reason)
            Toast.makeText(requireContext(), R.string.CallLogFragment__deletion_failed, Toast.LENGTH_SHORT).show()
          }
        }
      }
      .addTo(disposables)
  }

  private inner class BottomActionBarControllerCallback : SignalBottomActionBarController.Callback {
    override fun onBottomActionBarVisibilityChanged(visibility: Int) = Unit
  }

  private inner class CallLogActionModeCallback : CallLogActionMode.Callback {
    override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
      val actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(callback)
      requireListener<Callback>().onMultiSelectStarted()
      signalBottomActionBarController.setVisibility(true)
      binding.fab.visible = false
      return actionMode
    }

    override fun onActionModeWillEnd() {
      requireListener<Callback>().onMultiSelectFinished()
      signalBottomActionBarController.setVisibility(false)
      binding.fab.visible = true
    }

    override fun getResources(): Resources = resources
    override fun onResetSelectionState() {
      viewModel.clearSelected()
    }
  }

  interface Callback {
    fun onMultiSelectStarted()
    fun onMultiSelectFinished()
  }
}
