/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.log

import android.database.Cursor
import androidx.annotation.VisibleForTesting
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.signal.core.util.Stopwatch
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.EnabledState
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.CallTable.Direction
import org.thoughtcrime.securesms.database.CallTable.Event
import org.thoughtcrime.securesms.database.CallTable.Type
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.hours

/**
 * Performs clustering and caching of call log entries. Refreshes itself when
 * a change occurs.
 */
class CallEventCache(
  private val executor: Executor = SignalExecutors.newCachedSingleThreadExecutor("call-event-cache", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD)
) {
  companion object {
    private val TAG = Log.tag(CallEventCache::class)
    private val MISSED_CALL_EVENTS: List<Int> = listOf(CallTable.Event.MISSED, CallTable.Event.MISSED_NOTIFICATION_PROFILE, CallTable.Event.NOT_ACCEPTED, CallTable.Event.DECLINED).map { it.code }

    @VisibleForTesting
    fun clusterCallEvents(records: List<CacheRecord>, filterState: FilterState): List<CallLogRow.Call> {
      val stopwatch = Stopwatch("call-log-cluster")

      val recordIterator = records.filter { filterState.matches(it) }.listIterator()
      stopwatch.split("filter")

      if (!recordIterator.hasNext()) {
        return emptyList()
      }

      val output = mutableListOf<CallLogRow.Call>()
      val groupCallStateMap = mutableMapOf<Long, CallLogRow.GroupCallState>()
      val canUserBeginCallMap = mutableMapOf<Long, Boolean>()
      val callLinksSeen = hashSetOf<Long>()

      while (recordIterator.hasNext()) {
        val log = recordIterator.readNextCallLog(filterState, groupCallStateMap, canUserBeginCallMap, callLinksSeen)
        if (log != null) {
          output += log
        }
      }

      stopwatch.split("grouping")
      stopwatch.stop(TAG)
      return output
    }

    private fun ListIterator<CacheRecord>.readNextCallLog(
      filterState: FilterState,
      groupCallStateMap: MutableMap<Long, CallLogRow.GroupCallState>,
      canUserBeginCallMap: MutableMap<Long, Boolean>,
      callLinksSeen: MutableSet<Long>
    ): CallLogRow.Call? {
      val parent = next()

      if (parent.type == Type.AD_HOC_CALL.code && !callLinksSeen.add(parent.peer)) {
        return null
      }

      val children = mutableSetOf<Long>()
      while (hasNext()) {
        val child = next()

        if (child.type == Type.AD_HOC_CALL.code) {
          continue
        }

        if (parent.peer == child.peer && parent.direction == child.direction && isEventMatch(parent, child) && isWithinTimeout(parent, child)) {
          children.add(child.rowId)
        } else {
          previous()
          break
        }
      }

      return createParentCallLogRow(parent, children, filterState, groupCallStateMap, canUserBeginCallMap)
    }

    private fun readDataFromDatabase(): List<CacheRecord> {
      val stopwatch = Stopwatch("call-log-read-db")

      val events = SignalDatabase.calls.getCallsForCache(10_000).readToList { it.readCacheRecord() }
      stopwatch.split("db[${events.count()}]")

      stopwatch.stop(TAG)
      return events
    }

    private fun isMissedCall(call: CacheRecord): Boolean {
      return call.event in MISSED_CALL_EVENTS || isMissedGroupCall(call)
    }

    private fun isEventMatch(parent: CacheRecord, child: CacheRecord): Boolean {
      val isParentMissedCallEvent = isMissedCall(parent)
      val isChildMissedCallEvent = isMissedCall(child)

      return (isParentMissedCallEvent && isChildMissedCallEvent) || (!isParentMissedCallEvent && !isChildMissedCallEvent)
    }

    private fun isMissedGroupCall(call: CacheRecord): Boolean {
      return call.event == CallTable.Event.GENERIC_GROUP_CALL.code && !call.didLocalUserJoin && !call.isGroupCallActive
    }

    private fun isWithinTimeout(parent: CacheRecord, child: CacheRecord): Boolean {
      return (child.timestamp - parent.timestamp) <= 4.hours.inWholeMilliseconds
    }

    private fun canUserBeginCall(peer: Recipient, decryptedGroup: ByteArray?): Boolean {
      return if (peer.isGroup && decryptedGroup != null) {
        val proto = DecryptedGroup.ADAPTER.decode(decryptedGroup)
        return proto.isAnnouncementGroup != EnabledState.ENABLED || proto.members
          .firstOrNull() { it.aciBytes == SignalStore.account.aci?.toByteString() }?.role == Member.Role.ADMINISTRATOR
      } else {
        true
      }
    }

    private fun getGroupCallState(body: String?): CallLogRow.GroupCallState {
      if (body != null) {
        val groupCallUpdateDetails = GroupCallUpdateDetailsUtil.parse(body)
        return CallLogRow.GroupCallState.fromDetails(groupCallUpdateDetails)
      } else {
        return CallLogRow.GroupCallState.NONE
      }
    }

    private fun createParentCallLogRow(
      parent: CacheRecord,
      children: Set<Long>,
      filterState: FilterState,
      groupCallStateCache: MutableMap<Long, CallLogRow.GroupCallState>,
      canUserBeginCallMap: MutableMap<Long, Boolean>
    ): CallLogRow.Call {
      val peer = Recipient.resolved(RecipientId.from(parent.peer))
      return CallLogRow.Call(
        record = CallTable.Call(
          callId = parent.callId,
          peer = RecipientId.from(parent.peer),
          type = Type.deserialize(parent.type),
          event = Event.deserialize(parent.event),
          direction = Direction.deserialize(parent.direction),
          timestamp = parent.timestamp,
          messageId = parent.messageId.takeIf { it > 0 },
          ringerRecipient = parent.ringerRecipient.takeIf { it > 0 }?.let { RecipientId.from(it) },
          isGroupCallActive = parent.isGroupCallActive,
          didLocalUserJoin = parent.didLocalUserJoin
        ),
        date = parent.timestamp,
        peer = peer,
        groupCallState = if (peer.isGroup) {
          groupCallStateCache.getOrPut(parent.peer) { getGroupCallState(parent.body) }
        } else {
          CallLogRow.GroupCallState.NONE
        },
        children = children,
        searchQuery = filterState.query,
        callLinkPeekInfo = AppDependencies.signalCallManager.peekInfoSnapshot[peer.id],
        canUserBeginCall = if (peer.isGroup) {
          if (peer.isActiveGroup) {
            canUserBeginCallMap.getOrPut(parent.peer) { canUserBeginCall(peer, parent.decryptedGroupBytes) }
          } else false
        } else true
      )
    }

    private fun Cursor.readCacheRecord(): CacheRecord {
      return CacheRecord(
        rowId = this.requireLong(CallTable.ID),
        callId = this.requireLong(CallTable.CALL_ID),
        timestamp = this.requireLong(CallTable.TIMESTAMP),
        type = this.requireInt(CallTable.TYPE),
        direction = this.requireInt(CallTable.DIRECTION),
        event = this.requireInt(CallTable.EVENT),
        ringerRecipient = this.requireLong(CallTable.RINGER),
        peer = this.requireLong(CallTable.PEER),
        isGroupCallActive = this.requireBoolean(CallTable.GROUP_CALL_ACTIVE),
        didLocalUserJoin = this.requireBoolean(CallTable.LOCAL_JOINED),
        messageId = this.requireLong(CallTable.MESSAGE_ID),
        body = this.requireString(MessageTable.BODY),
        decryptedGroupBytes = this.requireBlob(GroupTable.V2_DECRYPTED_GROUP)
      )
    }
  }

  private val cacheRecords = BehaviorSubject.createDefault<List<CacheRecord>>(emptyList())

  /**
   * Returns an [Observable] that can be listened to for updates to the data set. When the observable
   * is subscribed to, we will begin listening for call event changes. When it is disposed, we will stop.
   */
  fun listenForChanges(): Observable<Unit> {
    onDataSetInvalidated()

    val disposables = CompositeDisposable()

    disposables += CallLogRepository.listenForCallTableChanges()
      .subscribeOn(Schedulers.io())
      .subscribe {
        onDataSetInvalidated()
      }

    disposables += AppDependencies
      .signalCallManager
      .peekInfoCache
      .skipWhile { cache -> cache.isEmpty() || cache.values.all { it.isCompletelyInactive } }
      .subscribeOn(Schedulers.computation())
      .distinctUntilChanged()
      .subscribe {
        onDataSetInvalidated()
      }

    return cacheRecords.doOnDispose {
      disposables.clear()
    }.map { }
  }

  /**
   * Returns a list of call events according to the given [FilterState], [limit] and [offset].
   */
  fun getCallEvents(filterState: FilterState, limit: Int, offset: Int): List<CallLogRow.Call> {
    val events = clusterCallEvents(cacheRecords.value!!, filterState)
    val start = max(offset, 0)
    val end = min(start + limit, events.size)
    return events.subList(start, end)
  }

  /**
   * Returns the number of call events that match the given [FilterState]
   */
  fun getCallEventsCount(filterState: FilterState): Int {
    val events = clusterCallEvents(cacheRecords.value!!, filterState)
    return events.size
  }

  private fun onDataSetInvalidated() {
    executor.execute {
      cacheRecords.onNext(readDataFromDatabase())
    }
  }

  data class FilterState(
    val query: String = "",
    val filter: CallLogFilter = CallLogFilter.ALL
  ) {
    fun matches(cacheRecord: CacheRecord): Boolean {
      return isFilterMatch(cacheRecord, filter) && isQueryMatch(cacheRecord, query)
    }

    private fun isFilterMatch(cacheRecord: CacheRecord, filter: CallLogFilter): Boolean {
      return when (filter) {
        CallLogFilter.ALL -> true
        CallLogFilter.MISSED -> isMissedCall(cacheRecord)
        CallLogFilter.AD_HOC -> error("Not supported.")
      }
    }

    private fun isQueryMatch(cacheRecord: CacheRecord, query: String): Boolean {
      if (query.isEmpty()) {
        return true
      }

      val recipient = Recipient.resolved(RecipientId.from(cacheRecord.peer))
      return recipient.isMatch(query)
    }
  }

  class CacheRecord(
    val rowId: Long,
    val callId: Long,
    val peer: Long,
    val type: Int,
    val direction: Int,
    val event: Int,
    val messageId: Long,
    val timestamp: Long,
    val ringerRecipient: Long,
    val isGroupCallActive: Boolean,
    val didLocalUserJoin: Boolean,
    val body: String?,
    val decryptedGroupBytes: ByteArray?
  )
}
