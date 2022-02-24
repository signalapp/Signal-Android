package org.thoughtcrime.securesms.stories.my

import org.thoughtcrime.securesms.conversation.ConversationMessage

data class MyStoriesState(
  val distributionSets: List<DistributionSet> = emptyList()
) {

  data class DistributionSet(
    val label: String?,
    val stories: List<ConversationMessage>
  )
}
