package org.thoughtcrime.securesms.calls.log

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.calls.links.UpdateCallLinkRepository
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.CallLinkPeekJob
import org.thoughtcrime.securesms.jobs.CallLogEventSendJob
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult

class CallLogRepository(
  private val updateCallLinkRepository: UpdateCallLinkRepository = UpdateCallLinkRepository()
) : CallLogPagedDataSource.CallRepository {
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
      SignalDatabase.calls.deleteNonAdHocCallEvents(selectedCallRowIds)
    }.subscribeOn(Schedulers.io())
  }

  fun deleteAllCallLogsExcept(
    selectedCallRowIds: Set<Long>,
    missedOnly: Boolean
  ): Completable {
    return Completable.fromAction {
      SignalDatabase.calls.deleteAllNonAdHocCallEventsExcept(selectedCallRowIds, missedOnly)
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Delete all call events / unowned links and enqueue clear history job, and then
   * emit a clear history message.
   */
  fun deleteAllCallLogsOnOrBeforeNow(): Single<Int> {
    return Single.fromCallable {
      SignalDatabase.rawDatabase.withinTransaction {
        val now = System.currentTimeMillis()
        SignalDatabase.calls.deleteNonAdHocCallEventsOnOrBefore(now)
        SignalDatabase.callLinks.deleteNonAdminCallLinksOnOrBefore(now)
        ApplicationDependencies.getJobManager().add(CallLogEventSendJob.forClearHistory(now))
      }

      SignalDatabase.callLinks.getAllAdminCallLinksExcept(emptySet())
    }.flatMap(this::revokeAndCollectResults).map { -1 }.subscribeOn(Schedulers.io())
  }

  /**
   * Deletes the selected call links. We DELETE those links we don't have admin keys for,
   * and revoke the ones we *do* have admin keys for. We then perform a cleanup step on
   * terminate to clean up call events.
   */
  fun deleteSelectedCallLinks(
    selectedCallRowIds: Set<Long>,
    selectedRoomIds: Set<CallLinkRoomId>
  ): Single<Int> {
    return Single.fromCallable {
      val allCallLinkIds = SignalDatabase.calls.getCallLinkRoomIdsFromCallRowIds(selectedCallRowIds) + selectedRoomIds
      SignalDatabase.callLinks.deleteNonAdminCallLinks(allCallLinkIds)
      SignalDatabase.callLinks.getAdminCallLinks(allCallLinkIds)
    }.flatMap(this::revokeAndCollectResults).subscribeOn(Schedulers.io())
  }

  /**
   * Deletes all but the selected call links. We DELETE those links we don't have admin keys for,
   * and revoke the ones we *do* have admin keys for. We then perform a cleanup step on
   * terminate to clean up call events.
   */
  fun deleteAllCallLinksExcept(
    selectedCallRowIds: Set<Long>,
    selectedRoomIds: Set<CallLinkRoomId>
  ): Single<Int> {
    return Single.fromCallable {
      val allCallLinkIds = SignalDatabase.calls.getCallLinkRoomIdsFromCallRowIds(selectedCallRowIds) + selectedRoomIds
      SignalDatabase.callLinks.deleteAllNonAdminCallLinksExcept(allCallLinkIds)
      SignalDatabase.callLinks.getAllAdminCallLinksExcept(allCallLinkIds)
    }.flatMap(this::revokeAndCollectResults).subscribeOn(Schedulers.io())
  }

  private fun revokeAndCollectResults(callLinksToRevoke: Set<CallLinkTable.CallLink>): Single<Int> {
    return Single.merge(
      callLinksToRevoke.map {
        updateCallLinkRepository.revokeCallLink(it.credentials!!)
      }
    ).reduce(0) { acc, current ->
      acc + (if (current is UpdateCallLinkResult.Success) 0 else 1)
    }.doOnTerminate {
      SignalDatabase.calls.updateAdHocCallEventDeletionTimestamps()
    }.doOnDispose {
      SignalDatabase.calls.updateAdHocCallEventDeletionTimestamps()
    }
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
    }.subscribeOn(Schedulers.io())
  }
}
