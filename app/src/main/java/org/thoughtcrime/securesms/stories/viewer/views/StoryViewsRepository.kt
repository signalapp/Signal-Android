package org.thoughtcrime.securesms.stories.viewer.views

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.GroupReceiptDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient

class StoryViewsRepository {
  fun getViews(storyId: Long): Observable<List<StoryViewItemData>> {
    return Observable.create<List<StoryViewItemData>> { emitter ->
      fun refresh() {
        emitter.onNext(
          SignalDatabase.groupReceipts.getGroupReceiptInfo(storyId).filter {
            it.status == GroupReceiptDatabase.STATUS_VIEWED
          }.map {
            StoryViewItemData(
              recipient = Recipient.resolved(it.recipientId),
              timeViewedInMillis = it.timestamp
            )
          }
        )
      }

      val observer = DatabaseObserver.MessageObserver { refresh() }

      ApplicationDependencies.getDatabaseObserver().registerMessageUpdateObserver(observer)
      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer)
      }

      refresh()
    }.subscribeOn(Schedulers.io())
  }
}
