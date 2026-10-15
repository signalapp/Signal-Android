/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.reactions.any

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.navigationBarsCompat
import org.signal.mediakeyboard.screens.emojisheet.EmojiSheet
import org.signal.mediakeyboard.screens.emojisheet.EmojiSheetAction
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediakeyboard.SignalEmojiSheetRepository
import org.thoughtcrime.securesms.reactions.edit.EditReactionsActivity
import org.thoughtcrime.securesms.util.fragments.findListener

/**
 * Lets the user pick any emoji, to react with or fill in elsewhere. The pick goes to the nearest parent [Callback].
 */
class ReactWithAnyEmojiBottomSheetDialogFragment : ComposeBottomSheetDialogFragment() {

  companion object {
    const val REACTION_STORAGE_KEY = "reactions_recent_emoji"
    private const val ABOUT_STORAGE_KEY = RecentEmojiPageModel.RECENT_STORAGE_KEY

    private const val ARG_MESSAGE_ID = "arg_message_id"
    private const val ARG_SHADOWS = "arg_shadows"
    private const val ARG_RECENT_KEY = "arg_recent_key"
    private const val ARG_EDIT = "arg_edit"
    private const val ARG_DARK = "arg_dark"

    @JvmStatic
    fun createForStory(): DialogFragment {
      return create(recentKey = REACTION_STORAGE_KEY)
    }

    @JvmStatic
    fun createForMessageRecord(messageRecord: MessageRecord): DialogFragment {
      return create(recentKey = REACTION_STORAGE_KEY, messageId = messageRecord.id, edit = true)
    }

    @JvmStatic
    fun createForAboutSelection(): DialogFragment {
      return create(recentKey = ABOUT_STORAGE_KEY)
    }

    @JvmStatic
    fun createForEditReactions(): DialogFragment {
      return create(recentKey = REACTION_STORAGE_KEY, shadows = false)
    }

    @JvmStatic
    fun createForCallingReactions(): ReactWithAnyEmojiBottomSheetDialogFragment {
      return create(recentKey = REACTION_STORAGE_KEY, shadows = false, edit = true, dark = true)
    }

    private fun create(
      recentKey: String,
      messageId: Long = -1,
      shadows: Boolean = true,
      edit: Boolean = false,
      dark: Boolean = false
    ): ReactWithAnyEmojiBottomSheetDialogFragment {
      return ReactWithAnyEmojiBottomSheetDialogFragment().apply {
        arguments = bundleOf(
          ARG_RECENT_KEY to recentKey,
          ARG_MESSAGE_ID to messageId,
          ARG_SHADOWS to shadows,
          ARG_EDIT to edit,
          ARG_DARK to dark
        )
      }
    }
  }

  private val messageId: MessageId? by lazy {
    requireArguments().getLong(ARG_MESSAGE_ID, -1).takeIf { it > 0 }?.let { MessageId(it) }
  }

  private val repository: SignalEmojiSheetRepository by lazy {
    SignalEmojiSheetRepository(
      context = requireContext().applicationContext,
      recentsStorageKey = requireArguments().getString(ARG_RECENT_KEY, REACTION_STORAGE_KEY),
      messageId = messageId
    )
  }

  override val themeResId: Int = R.style.Widget_Signal_ReactWithAny

  override val forceDarkTheme: Boolean
    get() = requireArguments().getBoolean(ARG_DARK, false)

  // The category rail rides above the keyboard itself. See Widget.Signal.ReactWithAny.BottomSheet.
  override val applyImePadding: Boolean = false

  override val hasScrollingContent: Boolean = true

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState)

    if (!requireArguments().getBoolean(ARG_SHADOWS, true)) {
      dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    return dialog
  }

  @Composable
  override fun SheetContent() {
    EmojiSheet(
      repository = repository,
      onAction = ::onAction,
      showCustomizeReactions = requireArguments().getBoolean(ARG_EDIT, false),
      // The sheet handles side navigation bars; the content handles the bottom one and the keyboard.
      modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBarsCompat.only(WindowInsetsSides.Horizontal))
    )
  }

  private fun onAction(action: EmojiSheetAction) {
    when (action) {
      is EmojiSheetAction.EmojiSelected -> onEmojiSelected(action.emoji)
      EmojiSheetAction.CustomizeReactionsClicked -> {
        val intent = Intent(requireContext(), EditReactionsActivity::class.java)
          .putExtra(EditReactionsActivity.ARG_FORCE_DARK_MODE, requireArguments().getBoolean(ARG_DARK, false))
        startActivity(intent)
      }
    }
  }

  private fun onEmojiSelected(emoji: String) {
    if (messageId != null) {
      SignalStore.emoji.setPreferredVariation(emoji)
      repository.toggleReaction(emoji)
    }

    findListener<Callback>()?.onReactWithAnyEmojiSelected(emoji)
    dismiss()
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    findListener<Callback>()?.onReactWithAnyEmojiDialogDismissed()
  }

  interface Callback {
    fun onReactWithAnyEmojiDialogDismissed()

    fun onReactWithAnyEmojiSelected(emoji: String)
  }
}
