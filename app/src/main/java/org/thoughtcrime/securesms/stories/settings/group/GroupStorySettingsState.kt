package org.thoughtcrime.securesms.stories.settings.group

import org.thoughtcrime.securesms.recipients.Recipient

data class GroupStorySettingsState(
  val name: String = "",
  val members: List<Recipient> = emptyList(),
  val removed: Boolean = false
)
