/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R
import org.signal.core.ui.R as CoreUiR

enum class MessageContextAction(
  @IdRes val accessibilityActionId: Int,
  @StringRes val labelRes: Int,
  @DrawableRes val iconRes: Int
) {
  REPLY(
    accessibilityActionId = R.id.conversation_message_accessibility_reply_action,
    labelRes = R.string.conversation_selection__menu_reply,
    iconRes = R.drawable.symbol_reply_24
  ),
  EDIT(
    accessibilityActionId = R.id.conversation_message_accessibility_edit_action,
    labelRes = R.string.conversation_selection__menu_edit,
    iconRes = CoreUiR.drawable.symbol_edit_24
  ),
  FORWARD(
    accessibilityActionId = R.id.conversation_message_accessibility_forward_action,
    labelRes = R.string.conversation_selection__menu_forward,
    iconRes = CoreUiR.drawable.symbol_forward_24
  ),
  RESEND(
    accessibilityActionId = R.id.conversation_message_accessibility_resend_action,
    labelRes = R.string.conversation_selection__menu_resend_message,
    iconRes = R.drawable.symbol_refresh_24
  ),
  SAVE(
    accessibilityActionId = R.id.conversation_message_accessibility_save_action,
    labelRes = R.string.conversation_selection__menu_save,
    iconRes = R.drawable.symbol_save_android_24
  ),
  COPY(
    accessibilityActionId = R.id.conversation_message_accessibility_copy_action,
    labelRes = R.string.conversation_selection__menu_copy,
    iconRes = CoreUiR.drawable.symbol_copy_android_24
  ),
  PAYMENT_DETAILS(
    accessibilityActionId = R.id.conversation_message_accessibility_payment_details_action,
    labelRes = R.string.conversation_selection__menu_payment_details,
    iconRes = R.drawable.symbol_payment_24
  ),
  MULTI_SELECT(
    accessibilityActionId = R.id.conversation_message_accessibility_multiselect_action,
    labelRes = R.string.conversation_selection__menu_multi_select,
    iconRes = CoreUiR.drawable.symbol_check_circle_24
  ),
  VIEW_INFO(
    accessibilityActionId = R.id.conversation_message_accessibility_view_info_action,
    labelRes = R.string.conversation_selection__menu_message_details,
    iconRes = CoreUiR.drawable.symbol_info_24
  ),
  END_POLL(
    accessibilityActionId = R.id.conversation_message_accessibility_end_poll_action,
    labelRes = R.string.conversation_selection__menu_end_poll,
    iconRes = R.drawable.symbol_stop_24
  ),
  PIN_MESSAGE(
    accessibilityActionId = R.id.conversation_message_accessibility_pin_message_action,
    labelRes = R.string.conversation_selection__menu_pin_message,
    iconRes = R.drawable.symbol_pin_24
  ),
  UNPIN_MESSAGE(
    accessibilityActionId = R.id.conversation_message_accessibility_unpin_message_action,
    labelRes = R.string.conversation_selection__menu_unpin_message,
    iconRes = R.drawable.symbol_pin_slash_24
  ),
  DELETE(
    accessibilityActionId = R.id.conversation_message_accessibility_delete_action,
    labelRes = R.string.conversation_selection__menu_delete,
    iconRes = CoreUiR.drawable.symbol_trash_24
  );

  companion object {
    private val BY_ACCESSIBILITY_ID: Map<Int, MessageContextAction> =
      values().associateBy(MessageContextAction::accessibilityActionId)

    @JvmStatic
    fun fromAccessibilityActionId(actionId: Int): MessageContextAction? {
      return BY_ACCESSIBILITY_ID[actionId]
    }
  }
}
