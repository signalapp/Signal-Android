package org.thoughtcrime.securesms.stories.viewer.page

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.NoSuchMessageException
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.jobs.MultiDeviceViewedUpdateJob
import org.thoughtcrime.securesms.jobs.SendViewedReceiptJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class StoryViewerPageRepository(context: Context) {

  private val context = context.applicationContext

  private fun getStoryRecords(recipientId: RecipientId): Observable<List<MessageRecord>> {
    return Observable.create { emitter ->
      val recipient = Recipient.resolved(recipientId)

      fun refresh() {
        val stories = if (recipient.isMyStory) {
          SignalDatabase.mms.allOutgoingStories
        } else {
          SignalDatabase.mms.getAllStoriesFor(recipientId)
        }

        val results = mutableListOf<MessageRecord>()

        while (stories.next != null) {
          if (!(recipient.isMyStory && stories.current.recipient.isGroup)) {
            results.add(stories.current)
          }
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

  private fun getStoryPostFromRecord(recipientId: RecipientId, record: MessageRecord): Observable<StoryPost> {
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
          attachment = (record as MmsMessageRecord).slideDeck.firstSlide!!.asAttachment(),
          conversationMessage = ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, record),
          allowsReplies = record.storyType.isStoryWithReplies
        )

        emitter.onNext(story)
      }

      val recipient = Recipient.resolved(recipientId)

      val messageUpdateObserver = DatabaseObserver.MessageObserver {
        if (it.mms && it.id == record.id) {
          try {
            val messageRecord = SignalDatabase.mms.getMessageRecord(record.id)
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
        refresh(SignalDatabase.mms.getMessageRecord(record.id))
      }

      ApplicationDependencies.getDatabaseObserver().registerConversationObserver(record.threadId, conversationObserver)
      ApplicationDependencies.getDatabaseObserver().registerMessageUpdateObserver(messageUpdateObserver)

      val messageInsertObserver = DatabaseObserver.MessageObserver {
        refresh(SignalDatabase.mms.getMessageRecord(record.id))
      }

      if (recipient.isGroup) {
        ApplicationDependencies.getDatabaseObserver().registerMessageInsertObserver(record.threadId, messageInsertObserver)
      }

      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(conversationObserver)
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageUpdateObserver)

        if (recipient.isGroup) {
          ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageInsertObserver)
        }
      }

      refresh(record)
    }
  }

  fun forceDownload(post: StoryPost) {
    ApplicationDependencies.getJobManager().add(
      AttachmentDownloadJob(post.id, (post.attachment as DatabaseAttachment).attachmentId, true)
    )
  }

  fun getStoryPostsFor(recipientId: RecipientId): Observable<List<StoryPost>> {
    return getStoryRecords(recipientId)
      .switchMap { records ->
        val posts = records.map { getStoryPostFromRecord(recipientId, it) }
        if (posts.isEmpty()) {
          Observable.just(emptyList())
        } else {
          Observable.combineLatest(posts) { it.toList() as List<StoryPost> }
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
          ApplicationDependencies.getJobManager().add(
            SendViewedReceiptJob(
              markedMessageInfo.threadId,
              storyPost.sender.id,
              markedMessageInfo.syncMessageId.timetamp,
              MessageId(storyPost.id, true)
            )
          )
          MultiDeviceViewedUpdateJob.enqueue(listOf(markedMessageInfo.syncMessageId))
        }
      }
    }
  }
}
