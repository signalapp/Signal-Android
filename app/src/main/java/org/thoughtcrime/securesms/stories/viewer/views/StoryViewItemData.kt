package org.thoughtcrime.securesms.stories.viewer.views

import org.thoughtcrime.securesms.recipients.Recipient

data class StoryViewItemData(
  val recipient: Recipient,
  val timeViewedInMillis: Long
)
