package org.thoughtcrime.securesms.calls.log

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.CallLinkPeekJob

class CallLogRepository : CallLogPagedDataSource.CallRepository {
  override fun getCallsCount(query: String?, filter: CallLogFilter): Int {
    return SignalDatabase.calls.getCallsCount(query, filter)
  }

  override fun getCalls(query: String?, filter: CallLogFilter, start: Int, length: Int): List<CallLogRow> {
    return SignalDatabase.calls.getCalls(start, length, query, filter)
  }

  override fun getCallLinksCount(query: String?, filter: CallLogFilter): Int {
    return when (filter) {
      CallLogFilter.MISSED -> 0
      CallLogFilter.ALL, CallLogFilter.AD_HOC -> SignalDatabase.callLinks.getCallLinksCount(query)
    }
  }

  override fun getCallLinks(query: String?, filter: CallLogFilter, start: Int, length: Int): List<CallLogRow> {
    return when (filter) {
      CallLogFilter.MISSED -> emptyList()
      CallLogFilter.ALL, CallLogFilter.AD_HOC -> SignalDatabase.callLinks.getCallLinks(query, start, length)
    }
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
    selectedCallRowIds: Set<Long>,
    missedOnly: Boolean
  ): Completable {
    return Completable.fromAction {
      SignalDatabase.calls.deleteAllCallEventsExcept(selectedCallRowIds, missedOnly)
    }.observeOn(Schedulers.io())
  }

  fun peekCallLinks(): Completable {
    return Completable.fromAction {
      val callLinks: List<CallLogRow.CallLink> = SignalDatabase.callLinks.getCallLinks(
        query = null,
        offset = 0,
        limit = 10
      )

      val callEvents: List<CallLogRow.Call> = SignalDatabase.calls.getCalls(
        offset = 0,
        limit = 10,
        searchTerm = null,
        filter = CallLogFilter.AD_HOC
      )

      val recipients = (callLinks.map { it.recipient } + callEvents.map { it.peer }).toSet()

      val jobs = recipients.take(10).map {
        CallLinkPeekJob(it.id)
      }

      ApplicationDependencies.getJobManager().addAll(jobs)
    }.observeOn(Schedulers.io())
  }
}
