/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist

import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversationlist.model.Conversation

/**
 * Shared policy for conversation list accessibility actions.
 *
 * Centralizes the logic for which actions to expose, their labels, and action dispatch
 * to avoid duplication between ConversationListAdapter and ConversationListSearchModels.
 */
object ConversationListAccessibilityHelper {

  @JvmStatic
  @JvmOverloads
  fun addConversationActions(
    info: AccessibilityNodeInfo,
    context: Context,
    conversation: Conversation,
    isInSelectionMode: Boolean,
    includeSelect: Boolean = true
  ) {
    if (isInSelectionMode) {
      return
    }

    val isArchived = conversation.threadRecord.isArchived

    if (!isArchived) {
      addAction(
        info,
        R.id.conversation_list_accessibility_read_action,
        context.resources.getQuantityString(
          if (conversation.threadRecord.isRead)
            R.plurals.ConversationListFragment_unread_plural
          else
            R.plurals.ConversationListFragment_read_plural,
          1
        )
      )

      addAction(
        info,
        R.id.conversation_list_accessibility_pin_action,
        context.getString(
          if (conversation.threadRecord.isPinned)
            R.string.ConversationListFragment_unpin
          else
            R.string.ConversationListFragment_pin
        )
      )

      addAction(
        info,
        R.id.conversation_list_accessibility_mute_action,
        context.getString(
          if (conversation.threadRecord.recipient.live().get().isMuted)
            R.string.ConversationListFragment_unmute
          else
            R.string.ConversationListFragment_mute
        )
      )
    }

    if (includeSelect) {
      addAction(
        info,
        R.id.conversation_list_accessibility_select_action,
        context.getString(R.string.ConversationListFragment_select)
      )
    }

    addAction(
      info,
      R.id.conversation_list_accessibility_archive_action,
      context.getString(
        if (isArchived)
          R.string.ConversationListFragment_unarchive
        else
          R.string.ConversationListFragment_archive
      )
    )

    addAction(
      info,
      R.id.conversation_list_accessibility_delete_action,
      context.getString(R.string.ConversationListFragment_delete)
    )
  }

  @JvmStatic
  fun dispatchConversationAction(
    actionId: Int,
    conversation: Conversation,
    listener: OnAccessibilityActionListener
  ): Boolean {
    return when (actionId) {
      R.id.conversation_list_accessibility_read_action -> {
        listener.onAccessibilityAction(
          conversation,
          if (conversation.threadRecord.isRead)
            ThreadAccessibilityAction.MARK_AS_UNREAD
          else
            ThreadAccessibilityAction.MARK_AS_READ
        )
        true
      }

      R.id.conversation_list_accessibility_pin_action -> {
        listener.onAccessibilityAction(
          conversation,
          if (conversation.threadRecord.isPinned)
            ThreadAccessibilityAction.UNPIN
          else
            ThreadAccessibilityAction.PIN
        )
        true
      }

      R.id.conversation_list_accessibility_mute_action -> {
        listener.onAccessibilityAction(
          conversation,
          if (conversation.threadRecord.recipient.live().get().isMuted)
            ThreadAccessibilityAction.UNMUTE
          else
            ThreadAccessibilityAction.MUTE
        )
        true
      }

      R.id.conversation_list_accessibility_select_action -> {
        listener.onAccessibilityAction(
          conversation,
          ThreadAccessibilityAction.SELECT
        )
        true
      }

      R.id.conversation_list_accessibility_archive_action -> {
        listener.onAccessibilityAction(
          conversation,
          if (conversation.threadRecord.isArchived)
            ThreadAccessibilityAction.UNARCHIVE
          else
            ThreadAccessibilityAction.ARCHIVE
        )
        true
      }

      R.id.conversation_list_accessibility_delete_action -> {
        listener.onAccessibilityAction(
          conversation,
          ThreadAccessibilityAction.DELETE
        )
        true
      }

      else -> false
    }
  }

  private fun addAction(info: AccessibilityNodeInfo, id: Int, label: String) {
    info.addAction(AccessibilityNodeInfo.AccessibilityAction(id, label))
  }

  interface OnAccessibilityActionListener {
    fun onAccessibilityAction(conversation: Conversation, action: ThreadAccessibilityAction)
  }
}
