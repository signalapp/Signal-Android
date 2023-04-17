package org.thoughtcrime.securesms.calls.log

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
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

  fun markAllCallEventsRead() {
    SignalExecutors.BOUNDED_IO.execute {
      SignalDatabase.messages.markAllCallEventsRead()
    }
  }

  fun listenForChanges(): Observable<Unit> {
    return Observable.create { emitter ->
      fun refresh() {
        emitter.onNext(Unit)
      }

      val databaseObserver = DatabaseObserver.Observer {
        refresh()
      }

      ApplicationDependencies.getDatabaseObserver().registerConversationListObserver(databaseObserver)
      ApplicationDependencies.getDatabaseObserver().registerCallUpdateObserver(databaseObserver)

      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(databaseObserver)
      }
    }
  }

  fun deleteSelectedCallLogs(
    selectedCallRowIds: Set<Long>
  ): Completable {
    return Completable.fromAction {
      SignalDatabase.calls.deleteCallEvents(selectedCallRowIds)
    }.observeOn(Schedulers.io())
  }

  fun deleteAllCallLogsExcept(
    selectedCallRowIds: Set<Long>
  ): Completable {
    return Completable.fromAction {
      SignalDatabase.calls.deleteAllCallEventsExcept(selectedCallRowIds)
    }.observeOn(Schedulers.io())
  }
}
