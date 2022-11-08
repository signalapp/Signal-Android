package org.thoughtcrime.securesms.mediasend.v2.review

import android.content.DialogInterface
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.signal.core.util.EditTextUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.KeyboardEntryDialogFragment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQuery
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQuery.NoQuery
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryChangedListener
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryResultsController
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryViewModel
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerFragment
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerViewModel
import org.thoughtcrime.securesms.databinding.V2MediaAddMessageDialogFragmentBinding
import org.thoughtcrime.securesms.keyboard.KeyboardPage
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.v2.HudCommand
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.views.Stub
import org.thoughtcrime.securesms.util.visible

class AddMessageDialogFragment : KeyboardEntryDialogFragment(R.layout.v2_media_add_message_dialog_fragment) {

  private val viewModel: MediaSelectionViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  private val keyboardPagerViewModel: KeyboardPagerViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  private lateinit var mentionsViewModel: MentionsPickerViewModel

  private val inlineQueryViewModel: InlineQueryViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  private val binding by ViewBinderDelegate(V2MediaAddMessageDialogFragmentBinding::bind, onBindingWillBeDestroyed = { binding ->
    binding.content.addAMessageInput.setInlineQueryChangedListener(null)
    binding.content.addAMessageInput.setMentionValidator(null)
  })

  private lateinit var emojiDrawerStub: Stub<MediaKeyboard>
  private lateinit var inlineQueryResultsController: InlineQueryResultsController

  private var requestedEmojiDrawer: Boolean = false

  private var recipient: Recipient? = null

  private val disposables = CompositeDisposable()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    val themeWrapper = ContextThemeWrapper(inflater.context, R.style.TextSecure_DarkTheme)
    val themedInflater = LayoutInflater.from(themeWrapper)

    return super.onCreateView(themedInflater, container, savedInstanceState)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    emojiDrawerStub = Stub(binding.content.emojiDrawerStub)

    if (Stories.isFeatureEnabled()) {
      EditTextUtil.addGraphemeClusterLimitFilter(binding.content.addAMessageInput, Stories.MAX_CAPTION_SIZE)
    }

    binding.content.addAMessageInput.addTextChangedListener(afterTextChanged = {
      viewModel.updateAddAMessageCount(it)
    })

    binding.content.addAMessageInput.setText(requireArguments().getCharSequence(ARG_INITIAL_TEXT))

    if (SignalStore.settings().isPreferSystemEmoji) {
      binding.content.emojiToggle.visible = false
    } else {
      binding.content.emojiToggle.setOnClickListener { onEmojiToggleClicked() }
    }

    binding.hud.setOnClickListener { dismissAllowingStateLoss() }

    val confirm: View = view.findViewById(R.id.confirm_button)
    confirm.setOnClickListener { dismissAllowingStateLoss() }

    disposables += viewModel.watchAddAMessageCount().subscribe { count ->
      binding.content.addAMessageLimit.visible = count.shouldDisplayCount()
      binding.content.addAMessageLimit.text = count.getRemaining().toString()
    }

    disposables.add(
      viewModel.hudCommands.observeOn(AndroidSchedulers.mainThread()).subscribe {
        when (it) {
          HudCommand.OpenEmojiSearch -> openEmojiSearch()
          HudCommand.CloseEmojiSearch -> closeEmojiSearch()
          is HudCommand.EmojiKeyEvent -> onKeyEvent(it.keyEvent)
          is HudCommand.EmojiInsert -> onEmojiSelected(it.emoji)
          else -> Unit
        }
      }
    )

    initializeMentions()
  }

  override fun onResume() {
    super.onResume()

    requestedEmojiDrawer = false
    ViewUtil.focusAndShowKeyboard(binding.content.addAMessageInput)
  }

  override fun onPause() {
    super.onPause()

    ViewUtil.hideKeyboard(requireContext(), binding.content.addAMessageInput)
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    viewModel.setMessage(binding.content.addAMessageInput.text)
  }

  override fun onKeyboardHidden() {
    if (!requestedEmojiDrawer) {
      super.onKeyboardHidden()
    }
  }

  override fun onKeyboardShown() {
    super.onKeyboardShown()
    if (emojiDrawerStub.resolved() && emojiDrawerStub.get().isShowing && !emojiDrawerStub.get().isEmojiSearchMode) {
      emojiDrawerStub.get().hide(true)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    disposables.dispose()
  }

  private fun initializeMentions() {
    mentionsViewModel = ViewModelProvider(requireActivity(), MentionsPickerViewModel.Factory()).get(MentionsPickerViewModel::class.java)

    inlineQueryResultsController = InlineQueryResultsController(
      requireContext(),
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

    val recipientId: RecipientId = viewModel.destination.getRecipientSearchKey()?.recipientId ?: return

    Recipient.live(recipientId).observe(viewLifecycleOwner) { recipient ->
      this.recipient = recipient
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
      keyboardPagerViewModel.setOnlyPage(KeyboardPage.EMOJI)
      emojiDrawerStub.get().setFragmentManager(childFragmentManager)
      binding.content.emojiToggle.attach(emojiDrawerStub.get())
    }

    if (binding.hud.currentInput == emojiDrawerStub.get()) {
      requestedEmojiDrawer = false
      binding.hud.showSoftkey(binding.content.addAMessageInput)
    } else {
      requestedEmojiDrawer = true
      binding.hud.hideSoftkey(binding.content.addAMessageInput) {
        binding.hud.post {
          binding.hud.show(binding.content.addAMessageInput, emojiDrawerStub.get())
        }
      }
    }
  }

  private fun openEmojiSearch() {
    if (emojiDrawerStub.resolved()) {
      emojiDrawerStub.get().onOpenEmojiSearch()
    }
  }

  private fun closeEmojiSearch() {
    if (emojiDrawerStub.resolved()) {
      emojiDrawerStub.get().onCloseEmojiSearch()
    }
  }

  private fun onEmojiSelected(emoji: String?) {
    binding.content.addAMessageInput.insertEmoji(emoji)
  }

  private fun onKeyEvent(keyEvent: KeyEvent?) {
    binding.content.addAMessageInput.dispatchKeyEvent(keyEvent)
  }

  companion object {

    const val TAG = "ADD_MESSAGE_DIALOG_FRAGMENT"

    private const val ARG_INITIAL_TEXT = "arg.initial.text"

    fun show(fragmentManager: FragmentManager, initialText: CharSequence?) {
      AddMessageDialogFragment().apply {
        arguments = Bundle().apply {
          putCharSequence(ARG_INITIAL_TEXT, initialText)
        }
      }.show(fragmentManager, TAG)
    }
  }
}
