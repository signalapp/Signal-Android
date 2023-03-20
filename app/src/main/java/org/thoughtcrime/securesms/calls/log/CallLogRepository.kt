package org.thoughtcrime.securesms.calls.log

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies

class CallLogRepository : CallLogPagedDataSource.CallRepository {
  override fun getCallsCount(query: String?, filter: CallLogFilter): Int {
    return SignalDatabase.calls.getCallsCount(query, filter)
  }

  override fun getCalls(query: String?, filter: CallLogFilter, start: Int, length: Int): List<CallLogRow> {
    return SignalDatabase.calls.getCalls(start, length, query, filter)
  }

  fun listenForChanges(): Observable<Unit> {
    return Observable.create { emitter ->
      fun refresh() {
        emitter.onNext(Unit)
      }

      val databaseObserver = DatabaseObserver.Observer {
        refresh()
      }

      val messageObserver = DatabaseObserver.MessageObserver {
        refresh()
      }

      ApplicationDependencies.getDatabaseObserver().registerConversationListObserver(databaseObserver)
      ApplicationDependencies.getDatabaseObserver().registerMessageUpdateObserver(messageObserver)

      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(databaseObserver)
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageObserver)
      }
    }
  }

  fun deleteSelectedCallLogs(
    selectedMessageIds: Set<Long>
  ): Completable {
    return Completable.fromAction {
      SignalDatabase.messages.deleteCallUpdates(selectedMessageIds)
    }.observeOn(Schedulers.io())
  }

  fun deleteAllCallLogsExcept(
    selectedMessageIds: Set<Long>
  ): Completable {
    return Completable.fromAction {
      SignalDatabase.messages.deleteAllCallUpdatesExcept(selectedMessageIds)
    }.observeOn(Schedulers.io())
  }
}
