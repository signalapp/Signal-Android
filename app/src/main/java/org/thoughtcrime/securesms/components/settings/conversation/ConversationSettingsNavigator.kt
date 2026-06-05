/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation

import androidx.fragment.app.FragmentActivity
import org.thoughtcrime.securesms.main.MainNavigationChatDetailRouter
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Routes to the conversation settings screen, handling split-pane vs. standalone activity automatically.
 */
object ConversationSettingsNavigator {
  @JvmStatic
  fun navigate(
    activity: FragmentActivity,
    recipient: Recipient
  ) {
    if (activity is MainNavigationChatDetailRouter) {
      activity.goToChatDetail(MainNavigationDetailLocation.Chats.ConversationSettings(recipient.id))
      return
    }

    val intent = if (recipient.isPushGroup) {
      ConversationSettingsActivity.forGroup(activity, recipient.requireGroupId())
    } else {
      ConversationSettingsActivity.forRecipient(activity, recipient.id)
    }
    activity.startActivity(intent)
  }
}
