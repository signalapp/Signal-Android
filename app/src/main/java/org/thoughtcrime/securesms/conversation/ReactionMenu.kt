/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.content.Context
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.signal.core.ui.R as CoreUiR

/**
 * What the long press menu offers for a given message, and whether that message can be reacted to
 * at all. Lifted out of the reaction overlay view, which used to build this while also reaching in
 * to hide its own strip; [canReact] is that side effect made into a result.
 */
object ReactionMenu {

  /**
   * @param items The menu's rows, in order.
   * @param canReact False for a message that takes no reactions, which hides the emoji strip.
   */
  data class Menu(
    val items: List<ActionItem>,
    val canReact: Boolean
  )

  fun of(
    context: Context,
    conversationRecipient: Recipient,
    conversationMessage: ConversationMessage,
    isNonAdminInAnnouncementGroup: Boolean,
    canEditGroupInfo: Boolean,
    onAction: (ReactionAction) -> Unit
  ): Menu {
    val menuState = MenuState.getMenuState(
      conversationRecipient,
      conversationMessage.multiselectCollection.toSet(),
      false,
      isNonAdminInAnnouncementGroup,
      canEditGroupInfo
    )

    val items = mutableListOf<ActionItem>()

    fun add(iconRes: Int, titleRes: Int, action: ReactionAction) {
      items += ActionItem(iconRes = iconRes, title = context.getString(titleRes), action = { onAction(action) })
    }

    if (menuState.shouldShowReplyAction()) {
      add(R.drawable.symbol_reply_24, R.string.conversation_selection__menu_reply, ReactionAction.REPLY)
    }

    if (menuState.shouldShowEditAction()) {
      add(CoreUiR.drawable.symbol_edit_24, R.string.conversation_selection__menu_edit, ReactionAction.EDIT)
    }

    if (menuState.shouldShowForwardAction()) {
      add(CoreUiR.drawable.symbol_forward_24, R.string.conversation_selection__menu_forward, ReactionAction.FORWARD)
    }

    if (menuState.shouldShowResendAction()) {
      add(CoreUiR.drawable.symbol_refresh_24, R.string.conversation_selection__menu_resend_message, ReactionAction.RESEND)
    }

    if (menuState.shouldShowSaveAttachmentAction()) {
      add(CoreUiR.drawable.symbol_save_android_24, R.string.conversation_selection__menu_save, ReactionAction.DOWNLOAD)
    }

    if (menuState.shouldShowCopyAction()) {
      add(CoreUiR.drawable.symbol_copy_android_24, R.string.conversation_selection__menu_copy, ReactionAction.COPY)
    }

    if (menuState.shouldShowPaymentDetails()) {
      add(R.drawable.symbol_payment_24, R.string.conversation_selection__menu_payment_details, ReactionAction.PAYMENT_DETAILS)
    }

    add(CoreUiR.drawable.symbol_check_circle_24, R.string.conversation_selection__menu_multi_select, ReactionAction.MULTISELECT)

    if (menuState.shouldShowDetailsAction()) {
      add(CoreUiR.drawable.symbol_info_24, R.string.conversation_selection__menu_message_details, ReactionAction.VIEW_INFO)
    }

    if (menuState.shouldShowPollTerminateAction()) {
      add(R.drawable.symbol_stop_24, R.string.conversation_selection__menu_end_poll, ReactionAction.END_POLL)
    }

    if (menuState.shouldShowPinMessage()) {
      add(R.drawable.symbol_pin_24, R.string.conversation_selection__menu_pin_message, ReactionAction.PIN_MESSAGE)
    }

    if (menuState.showShowUnpinMessage()) {
      add(R.drawable.symbol_pin_slash_24, R.string.conversation_selection__menu_unpin_message, ReactionAction.UNPIN_MESSAGE)
    }

    if (menuState.shouldShowStarMessage()) {
      add(R.drawable.symbol_star_outline_24, R.string.conversation_selection__menu_star, ReactionAction.STAR_MESSAGE)
    }

    if (menuState.shouldShowUnstarMessage()) {
      add(R.drawable.symbol_star_outline_24, R.string.conversation_selection__menu_unstar, ReactionAction.UNSTAR_MESSAGE)
    }

    add(CoreUiR.drawable.symbol_trash_24, R.string.conversation_selection__menu_delete, ReactionAction.DELETE)

    return Menu(items = items, canReact = menuState.shouldShowReactions())
  }

  /** The reaction this message already carries from us, if any. */
  fun appliedEmoji(messageRecord: MessageRecord): String? {
    val self = Recipient.self().id.serialize()

    return messageRecord.reactions
      .firstOrNull { it.author.serialize() == self }
      ?.emoji
  }
}
