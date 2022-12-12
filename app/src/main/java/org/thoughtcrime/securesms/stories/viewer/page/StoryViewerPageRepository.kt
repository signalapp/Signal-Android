package org.thoughtcrime.securesms.stories.viewer.page

import android.content.Context
import android.net.Uri
import androidx.annotation.CheckResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.NoSuchMessageException
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceViewedUpdateJob
import org.thoughtcrime.securesms.jobs.SendViewedReceiptJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.Base64

/**
 * Open for testing.
 */
open class StoryViewerPageRepository(context: Context, private val storyViewStateCache: StoryViewStateCache) {

  companion object {
    private val TAG = Log.tag(StoryViewerPageRepository::class.java)
  }

  private val context = context.applicationContext

  fun isReadReceiptsEnabled(): Boolean = SignalStore.storyValues().viewedReceiptsEnabled

  private fun getStoryRecords(recipientId: RecipientId, isOutgoingOnly: Boolean): Observable<List<MessageRecord>> {
    return Observable.create { emitter ->
      val recipient = Recipient.resolved(recipientId)

      fun refresh() {
        val stories = if (recipient.isMyStory) {
          SignalDatabase.mms.getAllOutgoingStories(false, 100)
        } else if (isOutgoingOnly) {
          SignalDatabase.mms.getOutgoingStoriesTo(recipientId)
        } else {
          SignalDatabase.mms.getAllStoriesFor(recipientId, 100)
        }

        val results = stories.filterNot {
          recipient.isMyStory && it.recipient.isGroup
        }

        emitter.onNext(results)
      }

      val storyObserver = DatabaseObserver.Observer {
        refresh()
      }

      ApplicationDependencies.getDatabaseObserver().registerStoryObserver(recipientId, storyObserver)
      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(storyObserver)
      }

      refresh()
    }
  }

  private fun getStoryPostFromRecord(recipientId: RecipientId, originalRecord: MessageRecord): Observable<StoryPost> {
    return Observable.create { emitter ->
      fun refresh(record: MessageRecord) {
        val recipient = Recipient.resolved(recipientId)
        val story = StoryPost(
          id = record.id,
          sender = if (record.isOutgoing) Recipient.self() else record.individualRecipient,
          group = if (recipient.isGroup) recipient else null,
          distributionList = if (record.recipient.isDistributionList) record.recipient else null,
          viewCount = record.viewedReceiptCount,
          replyCount = SignalDatabase.mms.getNumberOfStoryReplies(record.id),
          dateInMilliseconds = record.dateSent,
          content = getContent(record as MmsMessageRecord),
          conversationMessage = ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, record),
          allowsReplies = record.storyType.isStoryWithReplies,
          hasSelfViewed = storyViewStateCache.getOrPut(record.id, if (record.isOutgoing) true else record.viewedReceiptCount > 0)
        )

        emitter.onNext(story)
      }

      val recordId = originalRecord.id
      val threadId = originalRecord.threadId
      val recipient = Recipient.resolved(recipientId)

      val messageUpdateObserver = DatabaseObserver.MessageObserver {
        if (it.mms && it.id == recordId) {
          try {
            val messageRecord = SignalDatabase.mms.getMessageRecord(recordId)
            if (messageRecord.isRemoteDelete) {
              emitter.onComplete()
            } else {
              refresh(messageRecord)
            }
          } catch (e: NoSuchMessageException) {
            emitter.onComplete()
          }
        }
      }

      val conversationObserver = DatabaseObserver.Observer {
        try {
          refresh(SignalDatabase.mms.getMessageRecord(recordId))
        } catch (e: NoSuchMessageException) {
          Log.w(TAG, "Message deleted during content refresh.", e)
        }
      }

      ApplicationDependencies.getDatabaseObserver().registerConversationObserver(threadId, conversationObserver)
      ApplicationDependencies.getDatabaseObserver().registerMessageUpdateObserver(messageUpdateObserver)

      val messageInsertObserver = DatabaseObserver.MessageObserver {
        refresh(SignalDatabase.mms.getMessageRecord(recordId))
      }

      if (recipient.isGroup) {
        ApplicationDependencies.getDatabaseObserver().registerMessageInsertObserver(threadId, messageInsertObserver)
      }

      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(conversationObserver)
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageUpdateObserver)

        if (recipient.isGroup) {
          ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageInsertObserver)
        }
      }

      refresh(originalRecord)
    }
  }

  fun forceDownload(post: StoryPost): Completable {
    return Stories.enqueueAttachmentsFromStoryForDownload(post.conversationMessage.messageRecord as MmsMessageRecord, true)
  }

  fun getStoryPostsFor(recipientId: RecipientId, isOutgoingOnly: Boolean): Observable<List<StoryPost>> {
    return getStoryRecords(recipientId, isOutgoingOnly)
      .switchMap { records ->
        val posts: List<Observable<StoryPost>> = records.map {
          getStoryPostFromRecord(recipientId, it).distinctUntilChanged()
        }
        if (posts.isEmpty()) {
          Observable.just(emptyList())
        } else {
          Observable.combineLatest(posts) { it.filterIsInstance<StoryPost>() }
        }
      }.observeOn(Schedulers.io())
  }

  fun hideStory(recipientId: RecipientId): Completable {
    return Completable.fromAction {
      SignalDatabase.recipients.setHideStory(recipientId, true)
    }.subscribeOn(Schedulers.io())
  }

  fun markViewed(storyPost: StoryPost) {
    if (!storyPost.conversationMessage.messageRecord.isOutgoing) {
      SignalExecutors.BOUNDED.execute {
        val markedMessageInfo = SignalDatabase.mms.setIncomingMessageViewed(storyPost.id)
        if (markedMessageInfo != null) {
          ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners()

          if (storyPost.sender.isReleaseNotes) {
            SignalStore.storyValues().userHasViewedOnboardingStory = true
            Stories.onStorySettingsChanged(Recipient.self().id)
          } else {
            ApplicationDependencies.getJobManager().add(
              SendViewedReceiptJob(
                markedMessageInfo.threadId,
                storyPost.sender.id,
                markedMessageInfo.syncMessageId.timetamp,
                MessageId(storyPost.id, true)
              )
            )
            MultiDeviceViewedUpdateJob.enqueue(listOf(markedMessageInfo.syncMessageId))

            val recipientId = storyPost.group?.id ?: storyPost.sender.id
            SignalDatabase.recipients.updateLastStoryViewTimestamp(recipientId)
            Stories.enqueueNextStoriesForDownload(recipientId, true, 5)
          }
        }
      }
    }
  }

  @CheckResult
  fun resend(messageRecord: MessageRecord): Completable {
    return Completable.fromAction {
      MessageSender.resend(ApplicationDependencies.getApplication(), messageRecord)
    }.subscribeOn(Schedulers.io())
  }

  private fun getContent(record: MmsMessageRecord): StoryPost.Content {
    return if (record.storyType.isTextStory || record.slideDeck.asAttachments().isEmpty()) {
      StoryPost.Content.TextContent(
        uri = Uri.parse("story_text_post://${record.id}"),
        recordId = record.id,
        hasBody = canParseToTextStory(record.body),
        length = getTextStoryLength(record.body)
      )
    } else {
      StoryPost.Content.AttachmentContent(
        attachment = record.slideDeck.asAttachments().first()
      )
    }
  }

  private fun getTextStoryLength(body: String): Int {
    return if (canParseToTextStory(body)) {
      val breakIteratorCompat = BreakIteratorCompat.getInstance()
      breakIteratorCompat.setText(StoryTextPost.parseFrom(Base64.decode(body)).body)
      breakIteratorCompat.countBreaks()
    } else {
      0
    }
  }

  private fun canParseToTextStory(body: String): Boolean {
    return if (body.isNotEmpty()) {
      try {
        StoryTextPost.parseFrom(Base64.decode(body))
        return true
      } catch (e: Exception) {
        false
      }
    } else {
      false
    }
  }
}
