package org.thoughtcrime.securesms.stories.viewer

import io.reactivex.rxjava3.core.Single
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Open for testing
 */
open class StoryViewerRepository {
  fun getStories(): Single<List<RecipientId>> {
    return Single.create { emitter ->
      val myStoriesId = SignalDatabase.recipients.getOrInsertFromDistributionListId(DistributionListId.MY_STORY)
      val myStories = Recipient.resolved(myStoriesId)
      val recipientIds = SignalDatabase.mms.orderedStoryRecipientsAndIds.groupBy {
        val recipient = Recipient.resolved(it.recipientId)
        if (recipient.isDistributionList) {
          myStories
        } else {
          recipient
        }
      }.keys.filterNot { it.shouldHideStory() }.map { it.id }

      emitter.onSuccess(
        if (recipientIds.contains(myStoriesId)) {
          listOf(myStoriesId) + (recipientIds - myStoriesId)
        } else {
          recipientIds
        }
      )
    }
  }
}
