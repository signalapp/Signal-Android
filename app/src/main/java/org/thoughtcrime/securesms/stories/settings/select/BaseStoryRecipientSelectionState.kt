package org.thoughtcrime.securesms.stories.settings.select

import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.recipients.RecipientId

data class BaseStoryRecipientSelectionState(
  val distributionListId: DistributionListId?,
  val privateStory: DistributionListRecord? = null,
  val selection: Set<RecipientId> = emptySet(),
  val isStartingSelection: Boolean = false
)
