package org.thoughtcrime.securesms.stories.viewer.info

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.GroupReceiptDatabase
import org.thoughtcrime.securesms.database.NoSuchMessageException
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies

/**
 * Gathers necessary message record and receipt data for a given story id.
 */
class StoryInfoRepository {

  companion object {
    private val TAG = Log.tag(StoryInfoRepository::class.java)
  }

  /**
   * Retrieves the StoryInfo for a given ID and emits a new item whenever the underlying
   * message record changes.
   */
  fun getStoryInfo(storyId: Long): Observable<StoryInfo> {
    return observeMessageRecord(storyId)
      .switchMap { record ->
        getReceiptInfo(storyId).map { receiptInfo ->
          StoryInfo(record, receiptInfo)
        }.toObservable()
      }
      .subscribeOn(Schedulers.io())
  }

  private fun observeMessageRecord(storyId: Long): Observable<MessageRecord> {
    return Observable.create { emitter ->
      fun refresh() {
        try {
          emitter.onNext(SignalDatabase.mms.getMessageRecord(storyId))
        } catch (e: NoSuchMessageException) {
          Log.w(TAG, "The story message disappeared. Terminating emission.")
          emitter.onComplete()
        }
      }

      val observer = DatabaseObserver.MessageObserver {
        if (it.mms && it.id == storyId) {
          refresh()
        }
      }

      ApplicationDependencies.getDatabaseObserver().registerMessageUpdateObserver(observer)
      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer)
      }

      refresh()
    }
  }

  private fun getReceiptInfo(storyId: Long): Single<List<GroupReceiptDatabase.GroupReceiptInfo>> {
    return Single.fromCallable {
      SignalDatabase.groupReceipts.getGroupReceiptInfo(storyId)
    }
  }

  /**
   * The message record and receipt info for a given story id.
   */
  data class StoryInfo(val messageRecord: MessageRecord, val receiptInfo: List<GroupReceiptDatabase.GroupReceiptInfo>)
}
