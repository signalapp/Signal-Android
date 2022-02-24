package org.thoughtcrime.securesms.stories.settings.story

import org.thoughtcrime.securesms.database.model.DistributionListPartialRecord

data class StorySettingsState(
  val privateStories: List<DistributionListPartialRecord> = emptyList()
)
