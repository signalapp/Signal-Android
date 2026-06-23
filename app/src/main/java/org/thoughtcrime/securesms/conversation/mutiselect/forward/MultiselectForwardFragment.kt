package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.launch
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.LocalFragmentManager
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.rememberIsSplitPane
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableArrayListCompat
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TooltipPopup
import org.thoughtcrime.securesms.components.WrapperDialogFragment
import org.thoughtcrime.securesms.contacts.paged.ContactSearchCallbacks
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchError
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.paged.ContactSearchPagedDataSourceRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.v2.stories.ChooseGroupStoryBottomSheet
import org.thoughtcrime.securesms.mediasend.v2.stories.ChooseStoryTypeBottomSheet
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.stories.GroupStoryEducationSheet
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.stories.Stories.getHeaderAction
import org.thoughtcrime.securesms.stories.settings.create.CreateStoryFlowDialogFragment
import org.thoughtcrime.securesms.stories.settings.create.CreateStoryWithViewersFragment
import org.thoughtcrime.securesms.stories.settings.privacy.ChooseInitialMyStoryMembershipBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.viewModel
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog
import org.thoughtcrime.securesms.util.visible

/**
 * Allows selection and optional sending to one or more users.
 *
 * This fragment is designed to be displayed in a Dialog fragment, and thus has two available constructors to display as a bottom sheet or full screen dialog.
 *
 * To customize the available recipients, a parent must implement `SearchConfigurationProvider`
 *
 * This fragment will emit one of two possible result values at the same key, `RESULT_KEY`:
 *
 * - If the arguments contain a non-empty list of MultiShareArgs, then messages will be sent when the selection is confirmed. This will result in `RESULT_SENT` being true.
 * - If the arguments contain an empty list of MultiShareArgs, then the selection will be returned on confirmation. This will result in `RESULT_SELECTION` being set.
 *
 * It is up to the user of this fragment to handle the result accordingly utilizing a fragment result listener.
 */
class MultiselectForwardFragment :
  ComposeFragment(),
  SafetyNumberBottomSheet.Callbacks,
  ChooseStoryTypeBottomSheet.Callback,
  GroupStoryEducationSheet.Callback,
  WrapperDialogFragment.WrapperDialogFragmentCallback,
  ChooseInitialMyStoryMembershipBottomSheetDialogFragment.Callback {

  private val viewModel: MultiselectForwardViewModel by viewModel {
    MultiselectForwardViewModel(args)
  }

  private val contactSearchViewModel: ContactSearchViewModel by viewModels {
    ContactSearchViewModel.Factory(
      selectionLimits = RemoteConfig.shareSelectionLimit,
      isMultiSelect = !args.selectSingleRecipient,
      repository = ContactSearchRepository(),
      performSafetyNumberChecks = true,
      arbitraryRepository = findListener<SearchConfigurationProvider>()?.getArbitraryRepository(),
      searchRepository = SearchRepository(requireContext().getString(R.string.note_to_self)),
      contactSearchPagedDataSourceRepository = ContactSearchPagedDataSourceRepository(requireContext())
    )
  }
  private val disposables = LifecycleDisposable()

  private lateinit var callback: Callback
  private var dismissibleDialog: SimpleProgressDialog.DismissibleDialog? = null
  private var handler: Handler? = null

  private var bottomBarHeightPx by mutableIntStateOf(0)

  private val args: MultiselectForwardFragmentArgs by lazy {
    requireArguments().getParcelableCompat(ARGS, MultiselectForwardFragmentArgs::class.java)!!
  }

  override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
    return if (parentFragment != null) {
      requireParentFragment().onGetLayoutInflater(savedInstanceState)
    } else {
      super.onGetLayoutInflater(savedInstanceState)
    }
  }

  @Composable
  override fun FragmentContent() {
    CompositionLocalProvider(LocalFragmentManager provides childFragmentManager) {
      MultiselectForwardScreen(
        isSplitPane = !args.isWrappedInBottomSheet && LocalResources.current.rememberIsSplitPane(),
        args = args,
        contactSearchViewModel = contactSearchViewModel,
        callback = callback,
        mapStateToConfiguration = this@MultiselectForwardFragment::getConfiguration,
        contactSearchCallbacks = remember { SearchCallbacks() },
        additionalEntries = findListener<SearchConfigurationProvider>()?.getAdditionalEntries() ?: persistentHashMapOf(),
        bottomContentPadding = with(LocalDensity.current) { bottomBarHeightPx.toDp() }
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    Log.d(TAG, "onViewCreated()")

    view.minimumHeight = resources.displayMetrics.heightPixels

    callback = requireListener()
    disposables.bindTo(viewLifecycleOwner.lifecycle)

    val container = callback.getContainer()
    val title: TextView? = container.findViewById(R.id.title)
    val bottomBar = ComposeView(requireContext())
    bottomBar.layoutParams = when (container) {
      is CoordinatorLayout -> CoordinatorLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { gravity = Gravity.BOTTOM }
      is FrameLayout -> FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM)
      else -> ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }
    bottomBar.setContent {
      val state by viewModel.bottomBarState.collectAsStateWithLifecycle()
      val imeController = LocalSoftwareKeyboardController.current
      val isSplitPane = !args.isWrappedInBottomSheet && LocalResources.current.rememberIsSplitPane()

      SignalTheme {
        Surface(
          color = remember { Color(callback.getDialogBackgroundColor()) }
        ) {
          MultiselectForwardBottomBar(
            state = state,
            events = {
              when (it) {
                is MultiselectForwardBottomBarEvent.AddMessageUpdate -> {
                  viewModel.setMessage(it.message)
                }
                MultiselectForwardBottomBarEvent.SendClick -> {
                  imeController?.hide()
                  onSend()
                }
              }
            },
            isSplitPane = isSplitPane,
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
          )
        }
      }
    }

    if (args.isWrappedInBottomSheet) {
      title?.setText(args.title)
    }

    bottomBar.visible = false

    bottomBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
      bottomBarHeightPx = if (bottomBar.isVisible) bottom - top else 0
    }

    container.addView(bottomBar)

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        contactSearchViewModel.selectionState.collect { contactSelection ->
          if (contactSelection.isNotEmpty() && args.selectSingleRecipient) {
            onSend()
            return@collect
          }

          viewModel.setShareSelection(
            contactSelection.map { key ->
              val contact = key.requireShareContact()
              contact.recipientId
                .map<MultiselectForwardBottomBarState.SelectedContact> { MultiselectForwardBottomBarState.SelectedContact.KnownRecipient(Recipient.resolved(it)) }
                .orElseGet {
                  MultiselectForwardBottomBarState.SelectedContact.UnknownRecipient(contact.number)
                }
            }
          )

          viewModel.setAddMessageVisible(!args.forceDisableAddMessage && contactSelection.any { key -> !key.requireRecipientSearchKey().isStory } && args.multiShareArgs.isNotEmpty())

          if (contactSelection.isNotEmpty() && !bottomBar.isVisible) {
            bottomBar.animation = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_fade_from_bottom)
            bottomBar.visible = true
            if (args.forceDisableAddMessage) {
              ViewUtil.hideKeyboard(requireContext(), bottomBar)
            }
          } else if (contactSelection.isEmpty() && bottomBar.isVisible) {
            bottomBar.animation = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_fade_to_bottom)
            bottomBar.visible = false
            bottomBarHeightPx = 0
          }
        }
      }
    }

    disposables += contactSearchViewModel.errorEventsStream
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe {
        val toastMessage: Int? = when (it) {
          ContactSearchError.CONTACT_NOT_SELECTABLE -> R.string.MultiselectForwardFragment__only_admins_can_send_messages_to_this_group
          ContactSearchError.HARD_LIMIT_REACHED -> R.string.MultiselectForwardFragment__you_cant_select_more_chats
          ContactSearchError.RECOMMENDED_LIMIT_REACHED -> null
        }

        toastMessage?.let { message ->
          Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
      }

    viewModel.state.observe(viewLifecycleOwner) {
      Log.d(TAG, "State change: ${it.stage.javaClass.simpleName}")

      when (it.stage) {
        MultiselectForwardState.Stage.Selection -> {}
        MultiselectForwardState.Stage.FirstConfirmation -> displayFirstSendConfirmation()
        is MultiselectForwardState.Stage.SafetyConfirmation -> displaySafetyNumberConfirmation(it.stage.identities, it.stage.selectedContacts)
        MultiselectForwardState.Stage.LoadingIdentities -> {}
        MultiselectForwardState.Stage.SendPending -> {
          handler?.removeCallbacksAndMessages(null)
          dismissibleDialog?.dismiss()
          dismissibleDialog = SimpleProgressDialog.showDelayed(requireContext())
        }

        MultiselectForwardState.Stage.SomeFailed -> dismissWithSuccess(R.plurals.MultiselectForwardFragment_messages_sent)
        MultiselectForwardState.Stage.AllFailed -> dismissAndShowToast(R.plurals.MultiselectForwardFragment_messages_failed_to_send)
        MultiselectForwardState.Stage.Success -> dismissWithSuccess(R.plurals.MultiselectForwardFragment_messages_sent)
        is MultiselectForwardState.Stage.SelectionConfirmed -> dismissWithSelection(it.stage.selectedContacts)
      }

      viewModel.setSendEnabled(it.stage == MultiselectForwardState.Stage.Selection)
    }

    setFragmentResultListener(CreateStoryWithViewersFragment.REQUEST_KEY) { _, bundle ->
      val recipientId: RecipientId = bundle.getParcelableCompat(CreateStoryWithViewersFragment.STORY_RECIPIENT, RecipientId::class.java)!!
      contactSearchViewModel.setKeysSelected(setOf(ContactSearchKey.RecipientSearchKey(recipientId, true)))
      contactSearchViewModel.setQuery(null)
    }

    setFragmentResultListener(ChooseGroupStoryBottomSheet.GROUP_STORY) { _, bundle ->
      val groups: Set<RecipientId> = bundle.getParcelableArrayListCompat(ChooseGroupStoryBottomSheet.RESULT_SET, RecipientId::class.java)?.toSet() ?: emptySet()
      val keys: Set<ContactSearchKey.RecipientSearchKey> = groups.map { ContactSearchKey.RecipientSearchKey(it, true) }.toSet()
      contactSearchViewModel.addToVisibleGroupStories(keys)
      contactSearchViewModel.setKeysSelected(keys)
      contactSearchViewModel.setQuery(null)
    }
  }

  override fun onResume() {
    super.onResume()

    Log.d(TAG, "onViewCreated()")

    val now = System.currentTimeMillis()
    val expiringMessages = args.multiShareArgs.filter { it.expiresAt > 0L }
    val firstToExpire = expiringMessages.minByOrNull { it.expiresAt }
    val earliestExpiration = firstToExpire?.expiresAt ?: -1L
    if (viewModel.state.value?.stage is MultiselectForwardState.Stage.SelectionConfirmed && contactSearchViewModel.getSelectedContacts().isNotEmpty()) {
      onCanceled()
    }
    if (earliestExpiration > 0) {
      if (earliestExpiration <= now) {
        handleMessageExpired()
      } else {
        handler = Handler(Looper.getMainLooper())
        handler?.postDelayed(this::handleMessageExpired, earliestExpiration - now)
      }
    }
  }

  override fun onPause() {
    super.onPause()

    handler?.removeCallbacksAndMessages(null)
  }

  override fun onDestroyView() {
    dismissibleDialog?.dismissNow()
    super.onDestroyView()
  }

  private fun displayFirstSendConfirmation() {
    SignalStore.tooltips.markMultiForwardDialogSeen()

    val messageCount = getMessageCount()

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.MultiselectForwardFragment__faster_forwards)
      .setMessage(R.string.MultiselectForwardFragment__forwarded_messages_are_now)
      .setPositiveButton(resources.getQuantityString(R.plurals.MultiselectForwardFragment_send_d_messages, messageCount, messageCount)) { d, _ ->
        d.dismiss()
        viewModel.confirmFirstSend(contactSearchViewModel.getSelectedContacts())
      }
      .setNegativeButton(android.R.string.cancel) { d, _ ->
        d.dismiss()
        viewModel.cancelSend()
      }
      .show()
  }

  private fun onSend() {
    viewModel.setSendEnabled(false)
    viewModel.send(contactSearchViewModel.getSelectedContacts())
  }

  private fun displaySafetyNumberConfirmation(identityRecords: List<IdentityRecord>, selectedContacts: List<ContactSearchKey>) {
    SafetyNumberBottomSheet
      .forIdentityRecordsAndDestinations(identityRecords, selectedContacts)
      .show(childFragmentManager)
  }

  private fun dismissWithSuccess(@PluralsRes toastTextResId: Int) {
    Log.d(TAG, "dismissWithSuccess() Selected: ${contactSearchViewModel.getSelectedContacts().map { it.toString() }}")

    requireListener<Callback>().setResult(
      Bundle().apply {
        putBoolean(RESULT_SENT, true)
      }
    )

    dismissAndShowToast(toastTextResId)
  }

  private fun dismissAndShowToast(@PluralsRes toastTextResId: Int) {
    Log.d(TAG, "dismissAndShowToast() Selected: ${contactSearchViewModel.getSelectedContacts().map { it.toString() }}")

    val argCount = getMessageCount()

    callback.onFinishForwardAction()
    dismissibleDialog?.dismiss()
    Toast.makeText(requireContext(), requireContext().resources.getQuantityString(toastTextResId, argCount), Toast.LENGTH_SHORT).show()
    callback.exitFlow()
  }

  private fun getMessageCount(): Int = args.multiShareArgs.size + if (viewModel.bottomBarState.value.message.isNotEmpty()) 1 else 0

  private fun handleMessageExpired() {
    Log.d(TAG, "handleMessageExpired")

    callback.onFinishForwardAction()
    dismissibleDialog?.dismiss()
    Toast.makeText(requireContext(), resources.getQuantityString(R.plurals.MultiselectForwardFragment__couldnt_forward_messages, args.multiShareArgs.size), Toast.LENGTH_LONG).show()
    callback.exitFlow()
  }

  private fun dismissWithSelection(selectedContacts: Set<ContactSearchKey>) {
    Log.d(TAG, "dismissWithSelection() Selected: ${selectedContacts.map { it.toString() }}")

    callback.onFinishForwardAction()
    dismissibleDialog?.dismiss()

    val resultsBundle = Bundle().apply {
      putParcelableArrayList(RESULT_SELECTION, ArrayList(selectedContacts.map { it.requireRecipientSearchKey() }))
    }

    callback.setResult(resultsBundle)
    callback.exitFlow()
  }

  override fun sendAnywayAfterSafetyNumberChangedInBottomSheet(destinations: List<ContactSearchKey.RecipientSearchKey>) {
    viewModel.confirmSafetySend(destinations.toSet())
  }

  override fun onMessageResentAfterSafetyNumberChangeInBottomSheet() {
    throw UnsupportedOperationException()
  }

  override fun onCanceled() {
    viewModel.cancelSend()
  }

  private fun getStorySendRequirements(): Stories.MediaTransform.SendRequirements {
    return requireListener<Callback>().getStorySendRequirements() ?: viewModel.snapshot.storySendRequirements
  }

  private fun filterContacts(view: View?, contactSet: Set<ContactSearchKey>): Set<ContactSearchKey> {
    val storySendRequirements = getStorySendRequirements()
    val resultsSet = contactSet.filterNot {
      it is ContactSearchKey.RecipientSearchKey && it.isStory && storySendRequirements == Stories.MediaTransform.SendRequirements.CAN_NOT_SEND
    }

    if (view != null && contactSet.any { it is ContactSearchKey.RecipientSearchKey && it.isStory }) {
      when (storySendRequirements) {
        Stories.MediaTransform.SendRequirements.REQUIRES_CLIP -> {
          displayTooltip(view, R.string.MultiselectForwardFragment__videos_will_be_trimmed)
        }

        Stories.MediaTransform.SendRequirements.CAN_NOT_SEND -> {
          displayTooltip(view, R.string.MultiselectForwardFragment__videos_sent_to_stories_cant)
        }

        Stories.MediaTransform.SendRequirements.VALID_DURATION -> Unit
      }
    }

    return resultsSet.toSet()
  }

  private fun displayTooltip(anchor: View, @StringRes text: Int) {
    // 22dp + gutter
    TooltipPopup
      .forTarget(anchor)
      .setText(text)
      .show(TooltipPopup.POSITION_BELOW)
  }

  private fun getConfiguration(contactSearchState: ContactSearchState): ContactSearchConfiguration {
    return findListener<SearchConfigurationProvider>()?.getSearchConfiguration(childFragmentManager, contactSearchState) ?: ContactSearchConfiguration.build {
      query = contactSearchState.query

      if (Stories.isFeatureEnabled() && isSelectedMediaValidForStories()) {
        val expandedConfig: ContactSearchConfiguration.ExpandConfig? = if (isSelectedMediaValidForNonStories()) {
          ContactSearchConfiguration.ExpandConfig(
            isExpanded = contactSearchState.expandedSections.contains(ContactSearchConfiguration.SectionKey.STORIES),
            maxCountWhenNotExpanded = {
              if (args.isWrappedInBottomSheet) 1 else it + 1
            }
          )
        } else {
          null
        }

        addSection(
          ContactSearchConfiguration.Section.Stories(
            groupStories = contactSearchState.groupStories,
            includeHeader = true,
            headerAction = getHeaderAction(childFragmentManager),
            expandConfig = expandedConfig
          )
        )
      }

      if (isSelectedMediaValidForNonStories()) {
        if (query.isNullOrEmpty()) {
          addSection(
            ContactSearchConfiguration.Section.Recents(
              includeHeader = true,
              includeSelf = true
            )
          )
        }

        addSection(
          ContactSearchConfiguration.Section.Individuals(
            includeHeader = true,
            transportType = ContactSearchConfiguration.TransportType.PUSH,
            includeSelfMode = RecipientTable.IncludeSelfMode.IncludeWithRemap(getString(R.string.note_to_self))
          )
        )

        if (!query.isNullOrEmpty()) {
          addSection(
            ContactSearchConfiguration.Section.GroupMembers(
              includeHeader = true
            )
          )
        }

        addSection(
          ContactSearchConfiguration.Section.Groups(
            includeHeader = true
          )
        )
      }
    }
  }

  private fun isSelectedMediaValidForStories(): Boolean {
    return !args.isViewOnce && args.multiShareArgs.all { it.isValidForStories }
  }

  private fun isSelectedMediaValidForNonStories(): Boolean {
    return args.multiShareArgs.all { it.isValidForNonStories }
  }

  override fun onGroupStoryClicked() {
    if (SignalStore.story.userHasSeenGroupStoryEducationSheet) {
      onGroupStoryEducationSheetNext()
    } else {
      GroupStoryEducationSheet().show(childFragmentManager, GroupStoryEducationSheet.KEY)
    }
  }

  override fun onNewStoryClicked() {
    CreateStoryFlowDialogFragment().show(parentFragmentManager, CreateStoryWithViewersFragment.REQUEST_KEY)
  }

  override fun onGroupStoryEducationSheetNext() {
    ChooseGroupStoryBottomSheet().show(parentFragmentManager, ChooseGroupStoryBottomSheet.GROUP_STORY)
  }

  override fun onWrapperDialogFragmentDismissed() {
    contactSearchViewModel.refresh()
  }

  override fun onMyStoryConfigured(recipientId: RecipientId) {
    contactSearchViewModel.setKeysSelected(setOf(ContactSearchKey.RecipientSearchKey(recipientId, true)))
    contactSearchViewModel.refresh()
  }

  private inner class SearchCallbacks : ContactSearchCallbacks.Simple() {
    override fun onBeforeContactsSelected(view: View?, contactSearchKeys: Set<ContactSearchKey>): Set<ContactSearchKey> {
      val filtered: Set<ContactSearchKey> = filterContacts(view, contactSearchKeys)
      Log.d(TAG, "onBeforeContactsSelected() Attempting to select: ${contactSearchKeys.map { it.toString() }}, Filtered selection: ${filtered.map { it.toString() }}")
      return filtered
    }
  }

  interface Callback {
    fun onFinishForwardAction()
    fun exitFlow()
    fun onSearchInputFocused()
    fun setResult(bundle: Bundle)
    fun getContainer(): ViewGroup
    fun getDialogBackgroundColor(): Int
    fun getStorySendRequirements(): Stories.MediaTransform.SendRequirements? = null

    /**
     * Called when the user presses the navigation icon in the toolbar. Defaults to exitFlow().
     */
    fun navigateUp() = exitFlow()
  }

  companion object {
    private val TAG = Log.tag(MultiselectForwardActivity::class.java)

    const val ARGS = "args"
    const val RESULT_KEY = "result_key"
    const val RESULT_SELECTION = "result_selection_recipients"
    const val RESULT_SENT = "result_sent"

    @JvmStatic
    fun showBottomSheet(supportFragmentManager: FragmentManager, multiselectForwardFragmentArgs: MultiselectForwardFragmentArgs) {
      val fragment = MultiselectForwardBottomSheet()

      showDialogFragment(supportFragmentManager, fragment, multiselectForwardFragmentArgs.copy(isWrappedInBottomSheet = true))
    }

    @JvmStatic
    fun showFullScreen(supportFragmentManager: FragmentManager, multiselectForwardFragmentArgs: MultiselectForwardFragmentArgs) {
      val fragment = MultiselectForwardFullScreenDialogFragment()

      showDialogFragment(supportFragmentManager, fragment, multiselectForwardFragmentArgs)
    }

    @JvmStatic
    fun create(multiselectForwardFragmentArgs: MultiselectForwardFragmentArgs): Fragment {
      return MultiselectForwardFragment().apply {
        arguments = bundleOf(ARGS to multiselectForwardFragmentArgs)
      }
    }

    private fun showDialogFragment(supportFragmentManager: FragmentManager, fragment: DialogFragment, multiselectForwardFragmentArgs: MultiselectForwardFragmentArgs) {
      fragment.arguments = bundleOf(ARGS to multiselectForwardFragmentArgs)

      fragment.show(supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
