/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import org.signal.core.util.Result
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsAction
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsRepository
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Blocking, unblocking, spam reporting, and avatar taps, which work the same way on every conversation type that offers
 * them. Takes plain values rather than a shared state type, so each view model keeps its own state shape.
 */
object BlockAndSpamHandler {

  private val TAG = Log.tag(BlockAndSpamHandler::class)

  fun blockAction(recipient: Recipient): ConversationSettingsAction {
    return if (recipient.isBlocked) {
      ConversationSettingsAction.ShowUnblockDialog(recipient)
    } else {
      ConversationSettingsAction.ShowBlockDialog(recipient)
    }
  }

  fun reportSpamAction(recipient: Recipient): ConversationSettingsAction {
    return ConversationSettingsAction.ShowReportSpamDialog(recipient, canBlock = !recipient.isBlocked)
  }

  /**
   * Where a tap on the header avatar should go. Every conversation type behaves the same way here: if there's a story to
   * watch we ask which one the user meant, otherwise we go straight to the full-size avatar.
   */
  fun avatarClickAction(
    recipient: Recipient,
    storyViewState: StoryViewState,
    storiesEnabled: Boolean
  ): ConversationSettingsAction? {
    return when {
      storiesEnabled && storyViewState != StoryViewState.NONE -> {
        ConversationSettingsAction.ShowStoryOrAvatarDialog(recipient.id, recipient.shouldHideStory)
      }

      !recipient.isSelf -> ConversationSettingsAction.ShowAvatarPreview(recipient.id)

      else -> null
    }
  }

  suspend fun reportSpam(
    recipient: Recipient,
    threadId: Long,
    repository: ConversationSettingsRepository,
    emitAction: suspend (ConversationSettingsAction) -> Unit
  ) {
    if (!canReportSpam(recipient, threadId)) {
      Log.w(TAG, "[ReportSpam] Nothing to report yet, ignoring.")
      return
    }

    repository.reportSpam(recipient.id, threadId)
    emitAction(ConversationSettingsAction.ShowSpamReported)
    emitAction(ConversationSettingsAction.GoToConversationList)
  }

  suspend fun blockAndReportSpam(
    recipient: Recipient,
    threadId: Long,
    repository: ConversationSettingsRepository,
    emitAction: suspend (ConversationSettingsAction) -> Unit
  ) {
    if (!canReportSpam(recipient, threadId)) {
      Log.w(TAG, "[BlockAndReportSpam] Nothing to report yet, ignoring.")
      return
    }

    when (val result = repository.blockAndReportSpam(recipient.id, threadId)) {
      is Result.Success -> {
        emitAction(ConversationSettingsAction.ShowSpamReportedAndBlocked)
        emitAction(ConversationSettingsAction.GoToConversationList)
      }

      is Result.Failure -> emitAction(ConversationSettingsAction.ShowBlockError(result.failure))
    }
  }

  private fun canReportSpam(recipient: Recipient, threadId: Long): Boolean {
    return threadId > 0 && recipient != Recipient.UNKNOWN
  }
}
