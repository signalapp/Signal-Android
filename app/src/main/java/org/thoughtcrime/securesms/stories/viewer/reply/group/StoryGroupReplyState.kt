package org.thoughtcrime.securesms.stories.viewer.reply.group

import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.recipients.RecipientId

data class StoryGroupReplyState(
  val noReplies: Boolean = true,
  val nameColors: Map<RecipientId, NameColor> = emptyMap(),
  val loadState: LoadState = LoadState.INIT
) {
  enum class LoadState {
    INIT,
    READY
  }
}
