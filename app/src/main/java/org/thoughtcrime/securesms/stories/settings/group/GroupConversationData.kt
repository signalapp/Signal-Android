package org.thoughtcrime.securesms.stories.settings.group

import org.signal.core.models.recipients.RecipientId

/**
 * Minimum data needed to launch ConversationActivity for a given grou
 */
data class GroupConversationData(
  val groupRecipientId: RecipientId,
  val groupThreadId: Long
)
