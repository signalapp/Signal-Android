package org.thoughtcrime.securesms.mediasend.v2.review

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.util.ViewUtil
import org.signal.core.ui.view.Stub
import org.signal.core.ui.viewModel
import org.signal.core.util.ByteLimitInputFilter
import org.signal.core.util.EditTextUtil
import org.signal.core.util.getParcelableCompat
import org.signal.mediasend.MediaSendFlowHudCommand
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.warning.guardAgainstRecoveryKeyPaste
import org.thoughtcrime.securesms.components.KeyboardEntryDialogFragment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardView
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQuery
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQuery.NoQuery
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryChangedListener
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryResultsController
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryViewModel
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerFragment
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerViewModel
import org.thoughtcrime.securesms.databinding.V2MediaAddMessageDialogFragmentBinding
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.MessageUtil
import org.thoughtcrime.securesms.util.visible

class AddMessageDialogFragment : KeyboardEntryDialogFragment(R.layout.v2_media_add_message_dialog_fragment) {

  private val viewModel: AddMessageViewModel by viewModel {
    AddMessageViewModel(initialMessage = requireArguments().getCharSequence(ARG_INITIAL_TEXT, null))
  }

  private lateinit var mentionsViewModel: MentionsPickerViewModel

  private val inlineQueryViewModel: InlineQueryViewModel by activityViewModels()

  private val binding by ViewBinderDelegate(V2MediaAddMessageDialogFragmentBinding::bind, onBindingWillBeDestroyed = { binding ->
    binding.content.addAMessageInput.setInlineQueryChangedListener(null)
    binding.content.addAMessageInput.setMentionValidator(null)
  })

  private lateinit var emojiDrawerStub: Stub<EmojiKeyboardView>
  private lateinit var inlineQueryResultsController: InlineQueryResultsController

  private var requestedEmojiDrawer: Boolean = false
  private var startWithEmojiKeyboard: Boolean = false
  private var displayingRecoveryKeyWarning: Boolean = false

  private var recipient: Recipient? = null

  private val disposables = CompositeDisposable()

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    startWithEmojiKeyboard = savedInstanceState == null && requireArguments().getBoolean(ARG_INITIAL_EMOJI_TOGGLE)

    val dialog = super.onCreateDialog(savedInstanceState)
    if (startWithEmojiKeyboard) {
      dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
    }

    return dialog
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    if (viewModel.isViewOnce) {
      dismissAllowingStateLoss()
      return
    }

    emojiDrawerStub = Stub(binding.content.emojiDrawerStub)

    if (Stories.isFeatureEnabled()) {
      EditTextUtil.addGraphemeClusterLimitFilter(binding.content.addAMessageInput, Stories.MAX_CAPTION_SIZE)
    }

    binding.content.addAMessageInput.addTextChangedListener(afterTextChanged = {
      viewModel.updateAddAMessageCount(it)
    })

    binding.content.addAMessageInput.setText(requireArguments().getCharSequence(ARG_INITIAL_TEXT))
    binding.content.addAMessageInput.addTextChangedListener { viewModel.message = it }
    binding.content.addAMessageInput.filters += ByteLimitInputFilter(MessageUtil.MAX_TOTAL_BODY_SIZE_BYTES)
    binding.content.addAMessageInput.guardAgainstRecoveryKeyPaste(
      host = this,
      onWarningShown = { displayingRecoveryKeyWarning = true },
      onWarningDismissed = {
        displayingRecoveryKeyWarning = false
        ViewUtil.focusAndShowKeyboard(binding.content.addAMessageInput)
      }
    )

    binding.content.emojiToggle.setOnClickListener { onEmojiToggleClicked() }

    binding.hud.setOnClickListener { dismissAllowingStateLoss() }

    binding.content.viewOnceToggle.setOnClickListener {
      viewModel.isViewOnce = true

      dismissAllowingStateLoss()
    }

    val confirm: View = view.findViewById(R.id.confirm_button)
    confirm.setOnClickListener { dismissAllowingStateLoss() }

    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
      viewModel.watchAddAMessageCount().collect { count ->
        binding.content.addAMessageLimit.visible = count.shouldDisplayCount()
        binding.content.addAMessageLimit.text = count.getRemaining().toString()
      }
    }

    binding.content.viewOnceToggle.visible = requireArguments().getBoolean(ARG_VIEW_ONCE_AVAILABLE, false)
    initializeMentions()
  }

  override fun onResume() {
    super.onResume()

    requestedEmojiDrawer = false
    if (startWithEmojiKeyboard) {
      startWithEmojiKeyboard = false
      binding.content.addAMessageInput.requestFocus()
      binding.hud.doOnLayout { onEmojiToggleClicked() }
    } else {
      ViewUtil.focusAndShowKeyboard(binding.content.addAMessageInput)
    }
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)

    setFragmentResult(
      REQUEST_KEY,
      Bundle().apply {
        putCharSequence(RESULT_MESSAGE, viewModel.message)
        putBoolean(RESULT_INCREMENT_VIEW_ONCE_STATE, viewModel.isViewOnce)
      }
    )
  }

  override fun onKeyboardHidden() {
    if (!requestedEmojiDrawer && !displayingRecoveryKeyWarning) {
      super.onKeyboardHidden()
    }
  }

  override fun onKeyboardShown() {
    super.onKeyboardShown()
    if (emojiDrawerStub.resolved() && emojiDrawerStub.get().isShowing) {
      if (emojiDrawerStub.get().isSearching) {
        binding.content.emojiToggle.setToIme()
      } else {
        emojiDrawerStub.get().hide(true)
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    disposables.dispose()
  }

  private fun initializeMentions() {
    mentionsViewModel = ViewModelProvider(requireActivity(), MentionsPickerViewModel.Factory()).get(MentionsPickerViewModel::class.java)

    inlineQueryResultsController = InlineQueryResultsController(
      inlineQueryViewModel,
      requireView().findViewById(R.id.background_holder),
      (requireView() as ViewGroup),
      binding.content.addAMessageInput,
      viewLifecycleOwner
    )

    binding.content.addAMessageInput.setInlineQueryChangedListener(object : InlineQueryChangedListener {
      override fun onQueryChanged(inlineQuery: InlineQuery) {
        when (inlineQuery) {
          is InlineQuery.Mention -> {
            recipient?.takeIf { it.isPushV2Group && it.isActiveGroup }.let {
              ensureMentionsContainerFilled()
              mentionsViewModel.onQueryChange(inlineQuery.query)
            }
            inlineQueryViewModel.onQueryChange(inlineQuery)
          }

          is InlineQuery.Emoji -> {
            inlineQueryViewModel.onQueryChange(inlineQuery)
            mentionsViewModel.onQueryChange(null)
          }

          is NoQuery -> {
            mentionsViewModel.onQueryChange(null)
            inlineQueryViewModel.onQueryChange(inlineQuery)
          }
        }
      }

      override fun clearQuery() {
        onQueryChanged(NoQuery)
      }
    })

    disposables += inlineQueryViewModel
      .selection
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { r -> binding.content.addAMessageInput.replaceText(r) }

    val recipientId: RecipientId = requireArguments().getParcelableCompat(ARG_DESTINATION, RecipientId::class.java) ?: return

    Recipient.live(recipientId).observe(viewLifecycleOwner) { recipient ->
      this.recipient = recipient

      val confirmButton = requireView().findViewById<View>(R.id.confirm_button)
      ViewCompat.setBackgroundTintList(confirmButton, ColorStateList.valueOf(recipient.chatColors.asSingleColor()))
      mentionsViewModel.onRecipientChange(recipient)

      binding.content.addAMessageInput.setMentionValidator { annotations ->
        if (!recipient.isPushV2Group) {
          annotations
        } else {
          val validRecipientIds: Set<String> = recipient.participantIds
            .map { id -> MentionAnnotation.idToMentionAnnotationValue(id) }
            .toSet()

          annotations
            .filter { !validRecipientIds.contains(it.value) }
            .toList()
        }
      }
    }

    mentionsViewModel.selectedRecipient.observe(viewLifecycleOwner) { recipient ->
      binding.content.addAMessageInput.replaceTextWithMention(recipient.getDisplayName(requireContext()), recipient.id)
    }
  }

  private fun ensureMentionsContainerFilled() {
    val mentionsFragment = childFragmentManager.findFragmentById(R.id.mentions_picker_container)
    if (mentionsFragment == null) {
      childFragmentManager
        .beginTransaction()
        .replace(R.id.mentions_picker_container, MentionsPickerFragment())
        .commitNowAllowingStateLoss()
    }
  }

  private fun onEmojiToggleClicked() {
    if (!emojiDrawerStub.resolved()) {
      emojiDrawerStub.get().attachTo(binding.content.addAMessageInput)
      binding.content.emojiToggle.attach(emojiDrawerStub.get())
    }

    if (binding.hud.currentInput == emojiDrawerStub.get()) {
      requestedEmojiDrawer = false
      binding.hud.showSoftkey(binding.content.addAMessageInput)
    } else {
      requestedEmojiDrawer = true
      binding.hud.show(binding.content.addAMessageInput, emojiDrawerStub.get())
    }
  }

  companion object {

    const val TAG = "ADD_MESSAGE_DIALOG_FRAGMENT"

    private const val ARG_INITIAL_TEXT = "arg.initial.text"
    private const val ARG_INITIAL_EMOJI_TOGGLE = "arg.initial.emojiToggle"
    private const val ARG_VIEW_ONCE_AVAILABLE = "arg.viewOnceAvailable"
    private const val ARG_DESTINATION = "arg.destination"

    const val REQUEST_KEY = "AddMessageViewModel__RequestKey"
    const val RESULT_INCREMENT_VIEW_ONCE_STATE = "AddMessageViewModel_IncrementViewOnceState"
    const val RESULT_MESSAGE = "AddMessageViewModel__Message"

    fun show(fragmentManager: FragmentManager, addAMessageDialog: MediaSendFlowHudCommand.ShowAddAMessageDialog, destination: RecipientId?) {
      return show(fragmentManager, addAMessageDialog.message, addAMessageDialog.startWithEmojiKeyboard, addAMessageDialog.isViewOnceAvailable, destination)
    }

    fun show(fragmentManager: FragmentManager, initialText: CharSequence?, startWithEmojiKeyboard: Boolean, isViewOnceAvailable: Boolean, destination: RecipientId?) {
      AddMessageDialogFragment().apply {
        arguments = Bundle().apply {
          putCharSequence(ARG_INITIAL_TEXT, initialText)
          putBoolean(ARG_INITIAL_EMOJI_TOGGLE, startWithEmojiKeyboard)
          putBoolean(ARG_VIEW_ONCE_AVAILABLE, isViewOnceAvailable)
          putParcelable(ARG_DESTINATION, destination)
        }
      }.show(fragmentManager, TAG)
    }
  }
}
