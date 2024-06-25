/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.log

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.util.ThrottledDebouncer
import org.thoughtcrime.securesms.util.concurrent.SerialExecutor
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Peeks calls in the call log as data is loaded in, according to
 * an algorithm.
 */
class CallLogPeekHelper : DefaultLifecycleObserver {

  companion object {
    private val TAG = Log.tag(CallLogPeekHelper::class.java)
    private const val PEEK_SIZE = 10
  }

  private val executor: Executor = SerialExecutor(SignalExecutors.BOUNDED_IO)
  private val debouncer = ThrottledDebouncer(30.seconds.inWholeMilliseconds)
  private val dataSet = mutableSetOf<PeekEntry>()
  private val peekQueue = mutableSetOf<PeekEntry>()

  private var isFirstLoad = true
  private var isPaused = false

  override fun onResume(owner: LifecycleOwner) {
    executor.execute {
      isPaused = false
      performPeeks()
    }
  }

  override fun onPause(owner: LifecycleOwner) {
    executor.execute {
      isPaused = true
      debouncer.clear()
    }
  }

  /**
   * Called whenever the underlying datasource has been invalidated.
   */
  fun onDataSetInvalidated() {
    executor.execute {
      debouncer.clear()
      dataSet.clear()
      peekQueue.clear()
    }
  }

  /**
   * Called whenever a new page of data is loaded by the datasource.
   */
  fun onPageLoaded(pageData: List<CallLogRow>) {
    executor.execute {
      handleActiveCallLinks(pageData)
      handleActiveGroupCalls(pageData)
      handleInactiveGroupCalls(pageData)
      handleInactiveCallLinks(pageData)
      performPeeks()
    }
  }

  /**
   * Adds any and all active call links to our data set and queue
   */
  private fun handleActiveCallLinks(pageData: List<CallLogRow>) {
    val activeUnusedCallLinks: List<PeekEntry> = pageData.filterIsInstance<CallLogRow.CallLink>()
      .filter { it.callLinkPeekInfo?.isActive == true }
      .map { PeekEntry(it.recipient.id, PeekEntryIdentifier.CallLink(it.record.roomId, PeekEntryType.CALL_LINK)) }

    val activeCallLinksFromEvents: List<PeekEntry> = pageData.filterIsInstance<CallLogRow.Call>()
      .filter { it.peer.isCallLink && it.callLinkPeekInfo?.isActive == true }
      .map { PeekEntry(it.peer.id, PeekEntryIdentifier.Call(it.record.callId, PeekEntryType.CALL_LINK)) }

    val activeCallLinks: List<PeekEntry> = activeUnusedCallLinks + activeCallLinksFromEvents
    dataSet.addAll(activeCallLinks)
    peekQueue.addAll(activeCallLinks)
  }

  /**
   * Adds any and all active group calls to our dataset and queue.
   */
  private fun handleActiveGroupCalls(pageData: List<CallLogRow>) {
    val activeGroupCalls: List<PeekEntry> = pageData.filterIsInstance<CallLogRow.Call>()
      .filter { it.peer.isGroup && it.groupCallState != CallLogRow.GroupCallState.NONE }
      .map { PeekEntry(it.peer.id, PeekEntryIdentifier.Call(it.record.callId, PeekEntryType.GROUP_CALL)) }

    dataSet.addAll(activeGroupCalls)
    peekQueue.addAll(activeGroupCalls)
  }

  /**
   * Removes any and all inactive group calls from our dataset and queue.
   */
  private fun handleInactiveGroupCalls(pageData: List<CallLogRow>) {
    val inactiveGroupCalls: Set<PeekEntry> = pageData.filterIsInstance<CallLogRow.Call>()
      .filter { it.peer.isGroup && it.groupCallState == CallLogRow.GroupCallState.NONE }
      .map { PeekEntry(it.peer.id, PeekEntryIdentifier.Call(it.record.callId, PeekEntryType.GROUP_CALL)) }
      .toSet()

    peekQueue.removeAll(inactiveGroupCalls)
    dataSet.removeAll(inactiveGroupCalls)
  }

  /**
   * On first load, adds all inactive call links to our queue. On subsequent calls, removes them from the dataset.
   */
  private fun handleInactiveCallLinks(pageData: List<CallLogRow>) {
    val inactiveUnusedCallLinks: List<PeekEntry> = pageData.filterIsInstance<CallLogRow.CallLink>()
      .filter { it.callLinkPeekInfo?.isActive != true }
      .map { PeekEntry(it.recipient.id, PeekEntryIdentifier.CallLink(it.record.roomId, PeekEntryType.CALL_LINK)) }

    val inactiveCallLinksFromEvents: List<PeekEntry> = pageData.filterIsInstance<CallLogRow.Call>()
      .filter { it.callLinkPeekInfo?.isActive != true }
      .filter { it.record.timestamp <= 10.days.inWholeMilliseconds }
      .map { PeekEntry(it.peer.id, PeekEntryIdentifier.Call(it.record.callId, PeekEntryType.CALL_LINK)) }

    val inactiveCallLinks: Set<PeekEntry> = (inactiveUnusedCallLinks + inactiveCallLinksFromEvents).take(10).toSet()

    if (isFirstLoad) {
      isFirstLoad = false

      peekQueue.addAll(inactiveCallLinks)
    } else {
      dataSet.removeAll(inactiveCallLinks)
    }
  }

  private fun performPeeks() {
    executor.execute {
      if (peekQueue.isEmpty() || isPaused) {
        return@execute
      }

      Log.d(TAG, "Peeks in queue. Taking first $PEEK_SIZE.")

      val items = peekQueue.take(PEEK_SIZE)
      val remaining = peekQueue.drop(PEEK_SIZE)

      peekQueue.clear()
      peekQueue.addAll(remaining)

      items.forEach {
        when (it.identifier.peekEntryType) {
          PeekEntryType.CALL_LINK -> AppDependencies.signalCallManager.peekCallLinkCall(it.recipientId)
          PeekEntryType.GROUP_CALL -> AppDependencies.signalCallManager.peekGroupCall(it.recipientId)
        }
      }

      Log.d(TAG, "Began peeks for ${items.size} calls.")

      peekQueue.addAll(dataSet)
      debouncer.publish { performPeeks() }
    }
  }

  private enum class PeekEntryType {
    CALL_LINK,
    GROUP_CALL
  }

  private sealed interface PeekEntryIdentifier {
    val peekEntryType: PeekEntryType

    data class CallLink(private val roomId: CallLinkRoomId, override val peekEntryType: PeekEntryType = PeekEntryType.CALL_LINK) : PeekEntryIdentifier
    data class Call(private val callId: Long, override val peekEntryType: PeekEntryType) : PeekEntryIdentifier
  }

  private data class PeekEntry(
    val recipientId: RecipientId,
    val identifier: PeekEntryIdentifier
  )
}
