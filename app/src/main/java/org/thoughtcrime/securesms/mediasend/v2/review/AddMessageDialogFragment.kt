package org.thoughtcrime.securesms.mediasend.v2.review

import android.content.DialogInterface
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ComposeText
import org.thoughtcrime.securesms.components.InputAwareLayout
import org.thoughtcrime.securesms.components.KeyboardEntryDialogFragment
import org.thoughtcrime.securesms.components.emoji.EmojiToggle
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerFragment
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerViewModel
import org.thoughtcrime.securesms.keyboard.KeyboardPage
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.v2.HudCommand
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
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

  private val mentionsViewModel: MentionsPickerViewModel by viewModels(
    ownerProducer = { requireActivity() },
    factoryProducer = { MentionsPickerViewModel.Factory() }
  )

  private lateinit var input: ComposeText
  private lateinit var emojiDrawerToggle: EmojiToggle
  private lateinit var emojiDrawerStub: Stub<MediaKeyboard>
  private lateinit var hud: InputAwareLayout
  private lateinit var mentionsContainer: ViewGroup

  private var requestedEmojiDrawer: Boolean = false

  private val disposables = CompositeDisposable()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    val themeWrapper = ContextThemeWrapper(inflater.context, R.style.TextSecure_DarkTheme)
    val themedInflater = LayoutInflater.from(themeWrapper)

    return super.onCreateView(themedInflater, container, savedInstanceState)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    input = view.findViewById(R.id.add_a_message_input)
    input.setText(requireArguments().getCharSequence(ARG_INITIAL_TEXT))

    emojiDrawerToggle = view.findViewById(R.id.emoji_toggle)
    emojiDrawerStub = Stub(view.findViewById(R.id.emoji_drawer_stub))
    if (SignalStore.settings().isPreferSystemEmoji) {
      emojiDrawerToggle.visible = false
    } else {
      emojiDrawerToggle.setOnClickListener { onEmojiToggleClicked() }
    }

    hud = view.findViewById(R.id.hud)
    hud.setOnClickListener { dismissAllowingStateLoss() }

    val confirm: View = view.findViewById(R.id.confirm_button)
    confirm.setOnClickListener { dismissAllowingStateLoss() }

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
    ViewUtil.focusAndShowKeyboard(input)
  }

  override fun onPause() {
    super.onPause()

    ViewUtil.hideKeyboard(requireContext(), input)
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    viewModel.setMessage(input.text)
  }

  override fun onKeyboardHidden() {
    if (!requestedEmojiDrawer) {
      super.onKeyboardHidden()
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    disposables.dispose()

    input.setMentionQueryChangedListener(null)
    input.setMentionValidator(null)
  }

  private fun initializeMentions() {
    val recipientId: RecipientId = viewModel.destination.getRecipientSearchKey()?.recipientId ?: return

    mentionsContainer = requireView().findViewById(R.id.mentions_picker_container)

    Recipient.live(recipientId).observe(viewLifecycleOwner) { recipient ->
      mentionsViewModel.onRecipientChange(recipient)

      input.setMentionQueryChangedListener { query ->
        if (recipient.isPushV2Group) {
          ensureMentionsContainerFilled()
          mentionsViewModel.onQueryChange(query)
        }
      }

      input.setMentionValidator { annotations ->
        if (!recipient.isPushV2Group) {
          annotations
        } else {

          val validRecipientIds: Set<String> = recipient.participants
            .map { r -> MentionAnnotation.idToMentionAnnotationValue(r.id) }
            .toSet()

          annotations
            .filter { !validRecipientIds.contains(it.value) }
            .toList()
        }
      }
    }

    mentionsViewModel.selectedRecipient.observe(viewLifecycleOwner) { recipient ->
      input.replaceTextWithMention(recipient.getDisplayName(requireContext()), recipient.id)
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
      emojiDrawerToggle.attach(emojiDrawerStub.get())
    }

    if (hud.currentInput == emojiDrawerStub.get()) {
      requestedEmojiDrawer = false
      hud.showSoftkey(input)
    } else {
      requestedEmojiDrawer = true
      hud.hideSoftkey(input) {
        hud.post {
          hud.show(input, emojiDrawerStub.get())
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
    input.insertEmoji(emoji)
  }

  private fun onKeyEvent(keyEvent: KeyEvent?) {
    input.dispatchKeyEvent(keyEvent)
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
