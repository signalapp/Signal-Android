package org.thoughtcrime.securesms.stories.viewer.reply.direct

import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.recipients.Recipient

data class StoryDirectReplyState(
  val recipient: Recipient? = null,
  val storyRecord: MessageRecord? = null
)
