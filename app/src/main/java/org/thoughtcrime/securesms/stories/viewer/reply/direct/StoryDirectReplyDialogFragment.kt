package org.thoughtcrime.securesms.stories.viewer.reply.direct

import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.KeyboardEntryDialogFragment
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.keyboard.KeyboardPage
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageViewModel
import org.thoughtcrime.securesms.stories.viewer.reply.composer.StoryReplyComposer
import org.thoughtcrime.securesms.util.Dialogs
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * Dialog displayed when the user decides to send a private reply to a story.
 */
class StoryDirectReplyDialogFragment :
  KeyboardEntryDialogFragment(R.layout.stories_reply_to_story_fragment),
  EmojiKeyboardPageFragment.Callback,
  EmojiEventListener,
  EmojiSearchFragment.Callback,
  ReactWithAnyEmojiBottomSheetDialogFragment.Callback {

  private val lifecycleDisposable = LifecycleDisposable()
  private var isRequestingReactWithAny = false
  private var isReactClosingAfterSend = false

  override val themeResId: Int = R.style.Theme_Signal_RoundedBottomSheet_Stories

  private val viewModel: StoryDirectReplyViewModel by viewModels(
    factoryProducer = {
      StoryDirectReplyViewModel.Factory(storyId, recipientId, StoryDirectReplyRepository(requireContext()))
    }
  )

  private val keyboardPagerViewModel: KeyboardPagerViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  private val storyViewerPageViewModel: StoryViewerPageViewModel by viewModels(
    ownerProducer = { requireParentFragment() }
  )

  private lateinit var composer: StoryReplyComposer

  private val storyId: Long
    get() = requireArguments().getLong(ARG_STORY_ID)

  private val recipientId: RecipientId?
    get() = requireArguments().getParcelableCompat(ARG_RECIPIENT_ID, RecipientId::class.java)

  override val withDim: Boolean = true

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    lifecycleDisposable.bindTo(viewLifecycleOwner)

    composer = view.findViewById(R.id.input)
    composer.callback = object : StoryReplyComposer.Callback {
      override fun onSendActionClicked() {
        val sendReply = Runnable {
          val (body, _, bodyRanges) = composer.consumeInput()

          lifecycleDisposable += viewModel.sendReply(body, bodyRanges)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
              Toast.makeText(requireContext(), R.string.StoryDirectReplyDialogFragment__sending_reply, Toast.LENGTH_LONG).show()
              dismissAllowingStateLoss()
            }
        }

        if (SignalStore.uiHints.hasNotSeenTextFormattingAlert() && composer.input.hasStyling()) {
          Dialogs.showFormattedTextDialog(requireContext(), sendReply)
        } else {
          sendReply.run()
        }
      }

      override fun onReactionClicked(emoji: String) {
        sendReaction(emoji)
      }

      override fun onPickAnyReactionClicked() {
        isRequestingReactWithAny = true
        ReactWithAnyEmojiBottomSheetDialogFragment.createForStory().show(childFragmentManager, null)
      }

      override fun onInitializeEmojiDrawer(mediaKeyboard: MediaKeyboard) {
        keyboardPagerViewModel.setOnlyPage(KeyboardPage.EMOJI)
        mediaKeyboard.setFragmentManager(childFragmentManager)
      }
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      if (state.groupDirectReplyRecipient != null) {
        composer.displayReplyHint(state.groupDirectReplyRecipient)
      } else if (state.storyRecord != null) {
        composer.displayReplyHint(state.storyRecord.fromRecipient)
      }
    }
  }

  override fun onResume() {
    super.onResume()

    ViewUtil.focusAndShowKeyboard(composer.input)
  }

  override fun onPause() {
    super.onPause()

    ViewUtil.hideKeyboard(requireContext(), composer.input)
  }

  override fun openEmojiSearch() {
    composer.openEmojiSearch()
  }

  override fun onKeyboardHidden() {
    if (!composer.isRequestingEmojiDrawer && !isRequestingReactWithAny) {
      super.onKeyboardHidden()
    }
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    storyViewerPageViewModel.setIsDisplayingDirectReplyDialog(false)
  }

  companion object {
    const val REQUEST_EMOJI = "request.code.emoji"

    private const val ARG_STORY_ID = "arg.story.id"
    private const val ARG_RECIPIENT_ID = "arg.recipient.id"

    fun create(storyId: Long, recipientId: RecipientId? = null): DialogFragment {
      return StoryDirectReplyDialogFragment().apply {
        arguments = Bundle().apply {
          putLong(ARG_STORY_ID, storyId)
          putParcelable(ARG_RECIPIENT_ID, recipientId)
        }
      }
    }
  }

  override fun onEmojiSelected(emoji: String?) {
    composer.onEmojiSelected(emoji)
  }

  override fun closeEmojiSearch() {
    composer.closeEmojiSearch()
  }

  override fun onKeyEvent(keyEvent: KeyEvent?) = Unit

  override fun onReactWithAnyEmojiDialogDismissed() {
    isRequestingReactWithAny = false
    if (!isReactClosingAfterSend) {
      ViewUtil.focusAndShowKeyboard(composer.input)
    }
  }

  override fun onReactWithAnyEmojiSelected(emoji: String) {
    sendReaction(emoji)
    isReactClosingAfterSend = true
  }

  private fun sendReaction(emoji: String) {
    lifecycleDisposable += viewModel.sendReaction(emoji)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe {
        setFragmentResult(
          REQUEST_EMOJI,
          Bundle().apply {
            putString(REQUEST_EMOJI, emoji)
          }
        )
        dismissAllowingStateLoss()
      }
  }
}
