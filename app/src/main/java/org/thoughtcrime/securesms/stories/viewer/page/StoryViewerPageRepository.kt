package org.thoughtcrime.securesms.stories.viewer.page

import android.content.Context
import android.net.Uri
import androidx.annotation.CheckResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.Base64
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.GroupReceiptTable
import org.thoughtcrime.securesms.database.NoSuchMessageException
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceViewedUpdateJob
import org.thoughtcrime.securesms.jobs.SendViewedReceiptJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.stories.Stories

/**
 * Open for testing.
 */
open class StoryViewerPageRepository(context: Context, private val storyViewStateCache: StoryViewStateCache) {

  companion object {
    private val TAG = Log.tag(StoryViewerPageRepository::class.java)
  }

  private val context = context.applicationContext

  fun isReadReceiptsEnabled(): Boolean = SignalStore.story.viewedReceiptsEnabled

  private fun getStoryRecords(recipientId: RecipientId, isOutgoingOnly: Boolean): Observable<List<MessageRecord>> {
    return Observable.create { emitter ->
      val recipient = Recipient.resolved(recipientId)

      fun refresh() {
        val stories = if (recipient.isMyStory) {
          SignalDatabase.messages.getAllOutgoingStories(false, 100)
        } else if (isOutgoingOnly) {
          SignalDatabase.messages.getOutgoingStoriesTo(recipientId)
        } else {
          SignalDatabase.messages.getAllStoriesFor(recipientId, 100)
        }

        val results = stories.filterNot {
          recipient.isMyStory && it.toRecipient.isGroup
        }

        emitter.onNext(results)
      }

      val storyObserver = DatabaseObserver.Observer {
        refresh()
      }

      AppDependencies.databaseObserver.registerStoryObserver(recipientId, storyObserver)
      emitter.setCancellable {
        AppDependencies.databaseObserver.unregisterObserver(storyObserver)
      }

      refresh()
    }
  }

  private fun getStoryPostFromRecord(recipientId: RecipientId, originalRecord: MessageRecord): Observable<StoryPost> {
    return Observable.create { emitter ->
      fun refresh(record: MessageRecord) {
        val recipient = Recipient.resolved(recipientId)
        val viewedCount = SignalDatabase.groupReceipts.getGroupReceiptInfo(record.id).filter { it.status == GroupReceiptTable.STATUS_VIEWED }.size
        val story = StoryPost(
          id = record.id,
          sender = record.fromRecipient,
          group = if (recipient.isGroup) recipient else null,
          distributionList = if (record.toRecipient.isDistributionList) record.toRecipient else null,
          viewCount = viewedCount,
          replyCount = SignalDatabase.messages.getNumberOfStoryReplies(record.id),
          dateInMilliseconds = record.dateSent,
          content = getContent(record as MmsMessageRecord),
          conversationMessage = ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, record, recipient),
          allowsReplies = record.storyType.isStoryWithReplies,
          hasSelfViewed = storyViewStateCache.getOrPut(record.id, if (record.isOutgoing) true else record.isViewed())
        )

        emitter.onNext(story)
      }

      val recordId = originalRecord.id
      val threadId = originalRecord.threadId
      val recipient = Recipient.resolved(recipientId)

      val messageUpdateObserver = DatabaseObserver.MessageObserver {
        if (it.id == recordId) {
          try {
            val messageRecord = SignalDatabase.messages.getMessageRecord(recordId)
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
          refresh(SignalDatabase.messages.getMessageRecord(recordId))
        } catch (e: NoSuchMessageException) {
          Log.w(TAG, "Message deleted during content refresh.", e)
        }
      }

      AppDependencies.databaseObserver.registerConversationObserver(threadId, conversationObserver)
      AppDependencies.databaseObserver.registerMessageUpdateObserver(messageUpdateObserver)

      val messageInsertObserver = DatabaseObserver.MessageObserver {
        refresh(SignalDatabase.messages.getMessageRecord(recordId))
      }

      if (recipient.isGroup) {
        AppDependencies.databaseObserver.registerMessageInsertObserver(threadId, messageInsertObserver)
      }

      emitter.setCancellable {
        AppDependencies.databaseObserver.unregisterObserver(conversationObserver)
        AppDependencies.databaseObserver.unregisterObserver(messageUpdateObserver)

        if (recipient.isGroup) {
          AppDependencies.databaseObserver.unregisterObserver(messageInsertObserver)
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

  fun unhideStory(recipientId: RecipientId): Completable {
    return Completable.fromAction {
      SignalDatabase.recipients.setHideStory(recipientId, false)
    }.subscribeOn(Schedulers.io())
  }

  fun markViewed(storyPost: StoryPost) {
    if (!storyPost.conversationMessage.messageRecord.isOutgoing) {
      SignalExecutors.SERIAL.execute {
        val markedMessageInfo = SignalDatabase.messages.setIncomingMessageViewed(storyPost.id)
        if (markedMessageInfo != null) {
          AppDependencies.databaseObserver.notifyConversationListListeners()

          if (storyPost.sender.isReleaseNotes) {
            SignalStore.story.userHasViewedOnboardingStory = true
            Stories.onStorySettingsChanged(Recipient.self().id)
          } else {
            AppDependencies.jobManager.add(
              SendViewedReceiptJob(
                markedMessageInfo.threadId,
                storyPost.sender.id,
                markedMessageInfo.syncMessageId.timetamp,
                MessageId(storyPost.id)
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
      MessageSender.resend(AppDependencies.application, messageRecord)
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
      breakIteratorCompat.setText(StoryTextPost.ADAPTER.decode(Base64.decode(body)).body)
      breakIteratorCompat.countBreaks()
    } else {
      0
    }
  }

  private fun canParseToTextStory(body: String): Boolean {
    return if (body.isNotEmpty()) {
      try {
        StoryTextPost.ADAPTER.decode(Base64.decode(body))
        return true
      } catch (e: Exception) {
        false
      }
    } else {
      false
    }
  }
}
