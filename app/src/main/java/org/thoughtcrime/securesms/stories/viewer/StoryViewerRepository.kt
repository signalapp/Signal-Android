package org.thoughtcrime.securesms.stories.viewer

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.MessageDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Open for testing
 */
open class StoryViewerRepository {
  fun getFirstStory(recipientId: RecipientId, unviewedOnly: Boolean, storyId: Long): Single<MmsMessageRecord> {
    return if (storyId > 0) {
      Single.fromCallable {
        SignalDatabase.mms.getMessageRecord(storyId) as MmsMessageRecord
      }
    } else {
      Single.fromCallable {
        val recipient = Recipient.resolved(recipientId)
        val reader: MessageDatabase.Reader = if (recipient.isMyStory || recipient.isSelf) {
          SignalDatabase.mms.getAllOutgoingStories(false, 1)
        } else if (unviewedOnly) {
          SignalDatabase.mms.getUnreadStories(recipientId, 1)
        } else {
          SignalDatabase.mms.getAllStoriesFor(recipientId, 1)
        }

        reader.use { it.next } as MmsMessageRecord
      }
    }
  }

  fun getStories(hiddenStories: Boolean, unviewedOnly: Boolean, isOutgoingOnly: Boolean): Single<List<RecipientId>> {
    return Single.create<List<RecipientId>> { emitter ->
      val myStoriesId = SignalDatabase.recipients.getOrInsertFromDistributionListId(DistributionListId.MY_STORY)
      val myStories = Recipient.resolved(myStoriesId)
      val releaseChannelId = SignalStore.releaseChannelValues().releaseChannelRecipientId
      val recipientIds = SignalDatabase.mms.getOrderedStoryRecipientsAndIds(isOutgoingOnly).groupBy {
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
      }.filter {
        if (unviewedOnly) {
          if (it.isSelf || it.isMyStory) {
            false
          } else {
            SignalDatabase.mms.getStoryViewState(it.id) == StoryViewState.UNVIEWED
          }
        } else {
          true
        }
      }.map { it.id }

      emitter.onSuccess(
        recipientIds.floatToTop(releaseChannelId).floatToTop(myStoriesId)
      )
    }.subscribeOn(Schedulers.io())
  }

  private fun List<RecipientId>.floatToTop(recipientId: RecipientId?): List<RecipientId> {
    return if (recipientId != null && contains(recipientId)) {
      listOf(recipientId) + (this - recipientId)
    } else {
      this
    }
  }
}
