package org.thoughtcrime.securesms.stories.viewer.page

import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Dialogs that can be displayed and should override requests to continue playback of stories.
 * This assists in solving a race condition where one dialog opens another but the dismissal of
 * the first dialog resumes story playback after the new dialog requested a pause.
 */
sealed class StoryViewerDialog(val type: Type) {
  data class GroupDirectReply(
    val recipientId: RecipientId,
    val storyId: Long
  ) : StoryViewerDialog(Type.DIRECT_REPLY)

  object Forward : StoryViewerDialog(Type.FORWARD)
  object Delete : StoryViewerDialog(Type.DELETE)

  enum class Type {
    DIRECT_REPLY,
    FORWARD,
    DELETE,
    CONTEXT_MENU,
    VIEWS_AND_REPLIES
  }
}
