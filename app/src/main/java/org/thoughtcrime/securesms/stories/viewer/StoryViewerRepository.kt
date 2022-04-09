package org.thoughtcrime.securesms.stories.viewer

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Open for testing
 */
open class StoryViewerRepository {
  fun getStories(hiddenStories: Boolean): Single<List<RecipientId>> {
    return Single.create<List<RecipientId>> { emitter ->
      val myStoriesId = SignalDatabase.recipients.getOrInsertFromDistributionListId(DistributionListId.MY_STORY)
      val myStories = Recipient.resolved(myStoriesId)
      val recipientIds = SignalDatabase.mms.orderedStoryRecipientsAndIds.groupBy {
        val recipient = Recipient.resolved(it.recipientId)
        if (recipient.isDistributionList) {
          myStories
        } else {
          recipient
        }
      }.keys.filter {
        if (hiddenStories) {
          it.shouldHideStory()
        } else {
          !it.shouldHideStory()
        }
      }.map { it.id }

      emitter.onSuccess(
        if (recipientIds.contains(myStoriesId)) {
          listOf(myStoriesId) + (recipientIds - myStoriesId)
        } else {
          recipientIds
        }
      )
    }.subscribeOn(Schedulers.io())
  }
}
