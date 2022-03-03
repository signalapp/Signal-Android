package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.PluralsRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.contacts.HeaderAction
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.paged.ContactSearchMediator
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.conversation.ui.error.SafetyNumberChangeDialog
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.v2.stories.ChooseGroupStoryBottomSheet
import org.thoughtcrime.securesms.mediasend.v2.stories.ChooseStoryTypeBottomSheet
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.sharing.ShareSelectionAdapter
import org.thoughtcrime.securesms.sharing.ShareSelectionMappingModel
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.stories.settings.create.CreateStoryFlowDialogFragment
import org.thoughtcrime.securesms.stories.settings.create.CreateStoryWithViewersFragment
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog
import org.thoughtcrime.securesms.util.visible

class MultiselectForwardFragment :
  Fragment(),
  SafetyNumberChangeDialog.Callback,
  ChooseStoryTypeBottomSheet.Callback {

  private val viewModel: MultiselectForwardViewModel by viewModels(factoryProducer = this::createViewModelFactory)
  private val disposables = LifecycleDisposable()

  private lateinit var contactFilterView: ContactFilterView
  private lateinit var addMessage: EditText
  private lateinit var contactSearchMediator: ContactSearchMediator

  private lateinit var callback: Callback
  private var dismissibleDialog: SimpleProgressDialog.DismissibleDialog? = null
  private var handler: Handler? = null

  private fun createViewModelFactory(): MultiselectForwardViewModel.Factory {
    return MultiselectForwardViewModel.Factory(getMultiShareArgs(), MultiselectForwardRepository(requireContext()))
  }

  private fun getMultiShareArgs(): ArrayList<MultiShareArgs> = requireNotNull(requireArguments().getParcelableArrayList(ARG_MULTISHARE_ARGS))

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.multiselect_forward_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.minimumHeight = resources.displayMetrics.heightPixels

    val contactSearchRecycler: RecyclerView = view.findViewById(R.id.contact_selection_list)
    contactSearchMediator = ContactSearchMediator(this, contactSearchRecycler, FeatureFlags.shareSelectionLimit(), this::getConfiguration)

    callback = findListener()!!
    disposables.bindTo(viewLifecycleOwner.lifecycle)

    contactFilterView = view.findViewById(R.id.contact_filter_edit_text)

    contactFilterView.setOnSearchInputFocusChangedListener { _, hasFocus ->
      if (hasFocus) {
        callback.onSearchInputFocused()
      }
    }

    contactFilterView.setOnFilterChangedListener {
      contactSearchMediator.onFilterChanged(it)
    }

    val title: TextView? = view.findViewById(R.id.title)
    val container = callback.getContainer()
    val bottomBar = LayoutInflater.from(requireContext()).inflate(R.layout.multiselect_forward_fragment_bottom_bar, container, false)
    val shareSelectionRecycler: RecyclerView = bottomBar.findViewById(R.id.selected_list)
    val shareSelectionAdapter = ShareSelectionAdapter()
    val sendButton: View = bottomBar.findViewById(R.id.share_confirm)

    title?.setText(requireArguments().getInt(ARG_TITLE))

    addMessage = bottomBar.findViewById(R.id.add_message)

    sendButton.setOnClickListener {
      sendButton.isEnabled = false
      viewModel.send(addMessage.text.toString(), contactSearchMediator.getSelectedContacts())
    }

    shareSelectionRecycler.adapter = shareSelectionAdapter

    bottomBar.visible = false

    container.addView(bottomBar)

    contactSearchMediator.getSelectionState().observe(viewLifecycleOwner) {
      shareSelectionAdapter.submitList(it.mapIndexed { index, key -> ShareSelectionMappingModel(key.requireShareContact(), index == 0) })

      if (it.isNotEmpty() && !bottomBar.isVisible) {
        bottomBar.animation = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_fade_from_bottom)
        bottomBar.visible = true
      } else if (it.isEmpty() && bottomBar.isVisible) {
        bottomBar.animation = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_fade_to_bottom)
        bottomBar.visible = false
      }
    }

    viewModel.state.observe(viewLifecycleOwner) {
      when (it.stage) {
        MultiselectForwardState.Stage.Selection -> {}
        MultiselectForwardState.Stage.FirstConfirmation -> displayFirstSendConfirmation()
        is MultiselectForwardState.Stage.SafetyConfirmation -> displaySafetyNumberConfirmation(it.stage.identities)
        MultiselectForwardState.Stage.LoadingIdentities -> {}
        MultiselectForwardState.Stage.SendPending -> {
          handler?.removeCallbacksAndMessages(null)
          dismissibleDialog?.dismiss()
          dismissibleDialog = SimpleProgressDialog.showDelayed(requireContext())
        }
        MultiselectForwardState.Stage.SomeFailed -> dismissAndShowToast(R.plurals.MultiselectForwardFragment_messages_sent)
        MultiselectForwardState.Stage.AllFailed -> dismissAndShowToast(R.plurals.MultiselectForwardFragment_messages_failed_to_send)
        MultiselectForwardState.Stage.Success -> dismissAndShowToast(R.plurals.MultiselectForwardFragment_messages_sent)
        is MultiselectForwardState.Stage.SelectionConfirmed -> dismissWithSelection(it.stage.selectedContacts)
      }

      sendButton.isEnabled = it.stage == MultiselectForwardState.Stage.Selection
    }

    addMessage.visible = getMultiShareArgs().isNotEmpty()

    setFragmentResultListener(CreateStoryWithViewersFragment.REQUEST_KEY) { _, bundle ->
      val recipientId: RecipientId = bundle.getParcelable(CreateStoryWithViewersFragment.STORY_RECIPIENT)!!
      contactSearchMediator.setKeysSelected(setOf(ContactSearchKey.Story(recipientId)))
      contactFilterView.clear()
    }

    setFragmentResultListener(ChooseGroupStoryBottomSheet.GROUP_STORY) { _, bundle ->
      val groups: Set<RecipientId> = bundle.getParcelableArrayList<RecipientId>(ChooseGroupStoryBottomSheet.RESULT_SET)?.toSet() ?: emptySet()
      val keys: Set<ContactSearchKey.Story> = groups.map { ContactSearchKey.Story(it) }.toSet()
      contactSearchMediator.addToVisibleGroupStories(keys)
      contactSearchMediator.setKeysSelected(keys)
      contactFilterView.clear()
    }
  }

  override fun onResume() {
    super.onResume()

    val now = System.currentTimeMillis()
    val expiringMessages = getMultiShareArgs().filter { it.expiresAt > 0L }
    val firstToExpire = expiringMessages.minByOrNull { it.expiresAt }
    val earliestExpiration = firstToExpire?.expiresAt ?: -1L

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
    SignalStore.tooltips().markMultiForwardDialogSeen()

    val messageCount = getMessageCount()

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.MultiselectForwardFragment__faster_forwards)
      .setMessage(R.string.MultiselectForwardFragment__forwarded_messages_are_now)
      .setPositiveButton(resources.getQuantityString(R.plurals.MultiselectForwardFragment_send_d_messages, messageCount, messageCount)) { d, _ ->
        d.dismiss()
        viewModel.confirmFirstSend(addMessage.text.toString(), contactSearchMediator.getSelectedContacts())
      }
      .setNegativeButton(android.R.string.cancel) { d, _ ->
        d.dismiss()
        viewModel.cancelSend()
      }
      .show()
  }

  private fun displaySafetyNumberConfirmation(identityRecords: List<IdentityRecord>) {
    SafetyNumberChangeDialog.show(childFragmentManager, identityRecords)
  }

  private fun dismissAndShowToast(@PluralsRes toastTextResId: Int) {
    val argCount = getMessageCount()

    callback.onFinishForwardAction()
    dismissibleDialog?.dismiss()
    Toast.makeText(requireContext(), requireContext().resources.getQuantityString(toastTextResId, argCount), Toast.LENGTH_SHORT).show()
    callback.exitFlow()
  }

  private fun getMessageCount(): Int = getMultiShareArgs().size + if (addMessage.text.isNotEmpty()) 1 else 0

  private fun handleMessageExpired() {
    callback.onFinishForwardAction()
    dismissibleDialog?.dismiss()
    Toast.makeText(requireContext(), resources.getQuantityString(R.plurals.MultiselectForwardFragment__couldnt_forward_messages, getMultiShareArgs().size), Toast.LENGTH_LONG).show()
    callback.exitFlow()
  }

  private fun dismissWithSelection(selectedContacts: Set<ContactSearchKey>) {
    callback.onFinishForwardAction()
    dismissibleDialog?.dismiss()

    val resultsBundle = Bundle().apply {
      putParcelableArrayList(RESULT_SELECTION_RECIPIENTS, ArrayList(selectedContacts.map { it.requireParcelable() }))
    }

    callback.setResult(resultsBundle)
    callback.exitFlow()
  }

  override fun onSendAnywayAfterSafetyNumberChange(changedRecipients: MutableList<RecipientId>) {
    viewModel.confirmSafetySend(addMessage.text.toString(), contactSearchMediator.getSelectedContacts())
  }

  override fun onMessageResentAfterSafetyNumberChange() {
    throw UnsupportedOperationException()
  }

  override fun onCanceled() {
    viewModel.cancelSend()
  }

  private fun getHeaderAction(): HeaderAction {
    return HeaderAction(
      R.string.ContactsCursorLoader_new_story,
      R.drawable.ic_plus_20
    ) {
      ChooseStoryTypeBottomSheet().show(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  private fun getConfiguration(contactSearchState: ContactSearchState): ContactSearchConfiguration {
    return ContactSearchConfiguration.build {
      query = contactSearchState.query

      if (Stories.isFeatureEnabled() && isSelectedMediaValidForStories()) {
        addSection(
          ContactSearchConfiguration.Section.Stories(
            groupStories = contactSearchState.groupStories,
            includeHeader = true,
            headerAction = getHeaderAction(),
            expandConfig = ContactSearchConfiguration.ExpandConfig(
              isExpanded = contactSearchState.expandedSections.contains(ContactSearchConfiguration.SectionKey.STORIES)
            )
          )
        )
      }

      if (query.isNullOrEmpty()) {
        addSection(
          ContactSearchConfiguration.Section.Recents(
            includeHeader = true
          )
        )
      }

      addSection(
        ContactSearchConfiguration.Section.Individuals(
          includeHeader = true,
          transportType = if (includeSms()) ContactSearchConfiguration.TransportType.ALL else ContactSearchConfiguration.TransportType.PUSH,
          includeSelf = true
        )
      )

      addSection(
        ContactSearchConfiguration.Section.Groups(
          includeHeader = true,
          includeMms = includeSms()
        )
      )
    }
  }

  private fun includeSms(): Boolean {
    return Util.isDefaultSmsProvider(requireContext()) && requireArguments().getBoolean(ARG_CAN_SEND_TO_NON_PUSH)
  }

  private fun isSelectedMediaValidForStories(): Boolean {
    return getMultiShareArgs().all { it.isValidForStories }
  }

  override fun onGroupStoryClicked() {
    ChooseGroupStoryBottomSheet().show(parentFragmentManager, ChooseGroupStoryBottomSheet.GROUP_STORY)
  }

  override fun onNewStoryClicked() {
    CreateStoryFlowDialogFragment().show(parentFragmentManager, CreateStoryWithViewersFragment.REQUEST_KEY)
  }

  interface Callback {
    fun onFinishForwardAction()
    fun exitFlow()
    fun onSearchInputFocused()
    fun setResult(bundle: Bundle)
    fun getContainer(): ViewGroup
  }

  companion object {
    const val ARG_MULTISHARE_ARGS = "multiselect.forward.fragment.arg.multishare.args"
    const val ARG_CAN_SEND_TO_NON_PUSH = "multiselect.forward.fragment.arg.can.send.to.non.push"
    const val ARG_TITLE = "multiselect.forward.fragment.title"
    const val RESULT_SELECTION = "result_selection"
    const val RESULT_SELECTION_RECIPIENTS = "result_selection_recipients"

    @JvmStatic
    fun showBottomSheet(supportFragmentManager: FragmentManager, multiselectForwardFragmentArgs: MultiselectForwardFragmentArgs) {
      val fragment = MultiselectForwardBottomSheet()

      fragment.arguments = Bundle().apply {
        putParcelableArrayList(ARG_MULTISHARE_ARGS, ArrayList(multiselectForwardFragmentArgs.multiShareArgs))
        putBoolean(ARG_CAN_SEND_TO_NON_PUSH, multiselectForwardFragmentArgs.canSendToNonPush)
        putInt(ARG_TITLE, multiselectForwardFragmentArgs.title)
      }

      fragment.show(supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }

    @JvmStatic
    fun showFullScreen(supportFragmentManager: FragmentManager, multiselectForwardFragmentArgs: MultiselectForwardFragmentArgs) {
      val fragment = MultiselectForwardFullScreenDialogFragment()

      fragment.arguments = Bundle().apply {
        putParcelableArrayList(ARG_MULTISHARE_ARGS, ArrayList(multiselectForwardFragmentArgs.multiShareArgs))
        putBoolean(ARG_CAN_SEND_TO_NON_PUSH, multiselectForwardFragmentArgs.canSendToNonPush)
        putInt(ARG_TITLE, multiselectForwardFragmentArgs.title)
      }

      fragment.show(supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
