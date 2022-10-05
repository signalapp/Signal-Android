package org.thoughtcrime.securesms.stories.settings.custom

import org.thoughtcrime.securesms.database.model.DistributionListRecord

data class PrivateStorySettingsState(
  val privateStory: DistributionListRecord? = null,
  val areRepliesAndReactionsEnabled: Boolean = false,
  val isActionInProgress: Boolean = false
)
