package org.thoughtcrime.securesms.stories.viewer

import org.thoughtcrime.securesms.recipients.RecipientId

data class StoryViewerState(
  val pages: List<RecipientId> = emptyList(),
  val previousPage: Int = -1,
  val page: Int = -1
)
