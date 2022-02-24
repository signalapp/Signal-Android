package org.thoughtcrime.securesms.stories.tabs

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies

class ConversationListTabRepository {

  fun getNumberOfUnreadConversations(): Observable<Long> {
    return Observable.create<Long> {
      val listener = DatabaseObserver.Observer {
        it.onNext(SignalDatabase.threads.unreadThreadCount)
      }

      ApplicationDependencies.getDatabaseObserver().registerConversationListObserver(listener)
      it.setCancellable { ApplicationDependencies.getDatabaseObserver().unregisterObserver(listener) }
      it.onNext(SignalDatabase.threads.unreadThreadCount)
    }.subscribeOn(Schedulers.io())
  }

  fun getNumberOfUnseenStories(): Observable<Long> {
    return Observable.create<Long> {
      val listener = DatabaseObserver.Observer {
        it.onNext(SignalDatabase.mms.unreadStoryCount)
      }

      ApplicationDependencies.getDatabaseObserver().registerConversationListObserver(listener)
      it.setCancellable { ApplicationDependencies.getDatabaseObserver().unregisterObserver(listener) }
      it.onNext(SignalDatabase.mms.unreadStoryCount)
    }.subscribeOn(Schedulers.io())
  }
}
