package org.thoughtcrime.securesms.stories.viewer.info

/**
 * Contains the needed information to render the story info sheet.
 */
data class StoryInfoState(
  val sentMillis: Long = -1L,
  val receivedMillis: Long = -1L,
  val size: Long = -1L,
  val isOutgoing: Boolean = false,
  val recipients: List<StoryInfoRecipientRow.Model> = emptyList(),
  val isLoaded: Boolean = false
)
