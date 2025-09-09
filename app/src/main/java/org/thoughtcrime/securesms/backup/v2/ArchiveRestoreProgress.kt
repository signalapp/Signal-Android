/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import org.signal.core.util.bytes
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.core.util.throttleLatest
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.backup.RestoreState
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.impl.BatteryNotLowConstraint
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.WifiConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.safeUnregisterReceiver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * A class for tracking restore progress as a whole, but with a primary focus on managing media restore.
 *
 * Also provides helpful debugging information for attachment download speeds.
 */
object ArchiveRestoreProgress {
  private val TAG = Log.tag(ArchiveRestoreProgress::class.java)

  private var listenersRegistered = false
  private val listenerLock = ReentrantLock()

  private val attachmentObserver = DatabaseObserver.Observer {
    update()
  }

  private val networkChangeReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      update()
    }
  }

  private val store = MutableStateFlow(
    ArchiveRestoreProgressState(
      restoreState = SignalStore.backup.restoreState,
      remainingRestoreSize = SignalStore.backup.totalRestorableAttachmentSize.bytes,
      totalRestoreSize = SignalStore.backup.totalRestorableAttachmentSize.bytes,
      hasActivelyRestoredThisRun = SignalStore.backup.totalRestorableAttachmentSize > 0,
      totalToRestoreThisRun = SignalStore.backup.totalRestorableAttachmentSize.bytes,
      restoreStatus = ArchiveRestoreProgressState.RestoreStatus.NONE
    )
  )

  val state: ArchiveRestoreProgressState
    get() = store.value

  val stateFlow: Flow<ArchiveRestoreProgressState> = store
    .throttleLatest(1.seconds)
    .distinctUntilChanged()
    .flowOn(Dispatchers.IO)

  init {
    SignalExecutors.BOUNDED.execute { update() }
  }

  fun onRestorePending() {
    Log.i(TAG, "onRestorePending")
    SignalStore.backup.restoreState = RestoreState.PENDING
    update()
  }

  fun onStartMediaRestore() {
    Log.i(TAG, "onStartMediaRestore")
    SignalStore.backup.restoreState = RestoreState.CALCULATING_MEDIA
    SignalStore.backup.totalRestorableAttachmentSize = SignalDatabase.attachments.getRemainingRestorableAttachmentSize()
    update()
  }

  fun onRestoringMedia() {
    Log.i(TAG, "onRestoringMedia")
    SignalStore.backup.restoreState = RestoreState.RESTORING_MEDIA
    SignalStore.backup.totalRestorableAttachmentSize = SignalDatabase.attachments.getRemainingRestorableAttachmentSize()
    update()
  }

  fun onRestoringDb() {
    Log.i(TAG, "onRestoringDb")
    SignalStore.backup.restoreState = RestoreState.RESTORING_DB
    update()
  }

  fun onCancelMediaRestore() {
    Log.i(TAG, "onCancelMediaRestore")
    SignalStore.backup.restoreState = RestoreState.CANCELING_MEDIA
    update()
  }

  fun allMediaRestored() {
    val shouldUpdate = if (SignalStore.backup.restoreState == RestoreState.CANCELING_MEDIA) {
      Log.i(TAG, "allMediaCanceled")
      store.update { state ->
        if (state.restoreState == RestoreState.CANCELING_MEDIA) {
          state.copy(
            hasActivelyRestoredThisRun = false,
            totalToRestoreThisRun = 0.bytes
          )
        } else {
          state
        }
      }
      true
    } else if (SignalStore.backup.restoreState != RestoreState.NONE) {
      Log.i(TAG, "allMediaRestored")
      true
    } else {
      false
    }

    if (shouldUpdate) {
      SignalStore.backup.totalRestorableAttachmentSize = 0
      SignalStore.backup.restoreState = RestoreState.NONE
      update()
      onProcessEnd()
    }
  }

  @JvmStatic
  fun forceUpdate() {
    update()
  }

  fun clearFinishedStatus() {
    store.update { state ->
      if (state.restoreStatus == ArchiveRestoreProgressState.RestoreStatus.FINISHED) {
        state.copy(
          restoreStatus = ArchiveRestoreProgressState.RestoreStatus.NONE,
          hasActivelyRestoredThisRun = false,
          totalToRestoreThisRun = 0.bytes
        )
      } else {
        state
      }
    }
  }

  private fun update() {
    store.update { state ->
      val remainingRestoreSize = SignalDatabase.attachments.getRemainingRestorableAttachmentSize().bytes
      var restoreState = SignalStore.backup.restoreState

      if (restoreState.isMediaRestoreOperation) {
        if (remainingRestoreSize == 0.bytes && SignalStore.backup.totalRestorableAttachmentSize == 0L) {
          restoreState = RestoreState.NONE
          SignalStore.backup.restoreState = restoreState
          unregisterUpdateListeners()
        } else {
          registerUpdateListeners()
        }
      } else {
        unregisterUpdateListeners()
      }

      val status = when {
        !WifiConstraint.isMet(AppDependencies.application) && !SignalStore.backup.restoreWithCellular -> ArchiveRestoreProgressState.RestoreStatus.WAITING_FOR_WIFI
        !NetworkConstraint.isMet(AppDependencies.application) -> ArchiveRestoreProgressState.RestoreStatus.WAITING_FOR_INTERNET
        !BatteryNotLowConstraint.isMet() -> ArchiveRestoreProgressState.RestoreStatus.LOW_BATTERY
        restoreState == RestoreState.NONE -> if (state.hasActivelyRestoredThisRun) ArchiveRestoreProgressState.RestoreStatus.FINISHED else ArchiveRestoreProgressState.RestoreStatus.NONE
        else -> {
          val availableBytes = SignalStore.backup.spaceAvailableOnDiskBytes

          if (availableBytes > -1L && remainingRestoreSize > availableBytes.bytes) {
            ArchiveRestoreProgressState.RestoreStatus.NOT_ENOUGH_DISK_SPACE
          } else {
            ArchiveRestoreProgressState.RestoreStatus.RESTORING
          }
        }
      }

      val totalRestoreSize = SignalStore.backup.totalRestorableAttachmentSize.bytes

      state.copy(
        restoreState = restoreState,
        remainingRestoreSize = remainingRestoreSize,
        restoreStatus = status,
        totalRestoreSize = totalRestoreSize,
        hasActivelyRestoredThisRun = state.hasActivelyRestoredThisRun || totalRestoreSize > 0.bytes,
        totalToRestoreThisRun = if (totalRestoreSize > 0.bytes) totalRestoreSize else state.totalToRestoreThisRun
      )
    }
  }

  private fun registerUpdateListeners() {
    if (!listenersRegistered) {
      listenerLock.withLock {
        if (!listenersRegistered) {
          Log.i(TAG, "Registering progress related listeners")
          AppDependencies.databaseObserver.registerAttachmentUpdatedObserver(attachmentObserver)
          AppDependencies.application.registerReceiver(networkChangeReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
          AppDependencies.application.registerReceiver(networkChangeReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
          listenersRegistered = true
        }
      }
    }
  }

  private fun unregisterUpdateListeners() {
    if (listenersRegistered) {
      listenerLock.withLock {
        if (listenersRegistered) {
          Log.i(TAG, "Unregistering listeners")
          AppDependencies.databaseObserver.unregisterObserver(attachmentObserver)
          AppDependencies.application.safeUnregisterReceiver(networkChangeReceiver)
          listenersRegistered = false
        }
      }
    }
  }

  //region Attachment Debug

  private var debugAttachmentStartTime: Long = 0
  private val debugTotalAttachments: AtomicInteger = AtomicInteger(0)
  private val debugTotalBytes: AtomicLong = AtomicLong(0)

  private val attachmentProgress: MutableMap<AttachmentId, AttachmentProgressDetails> = ConcurrentHashMap()

  fun onProcessStart() {
    debugAttachmentStartTime = System.currentTimeMillis()
  }

  fun onDownloadStart(attachmentId: AttachmentId) {
    attachmentProgress[attachmentId] = AttachmentProgressDetails(startTimeMs = System.currentTimeMillis())
  }

  fun onDownloadEnd(attachmentId: AttachmentId, totalBytes: Long) {
    val details = attachmentProgress[attachmentId] ?: return
    details.networkFinishTime = System.currentTimeMillis()
    details.totalBytes = totalBytes
  }

  fun onWriteToDiskEnd(attachmentId: AttachmentId) {
    val details = attachmentProgress[attachmentId] ?: return
    attachmentProgress.remove(attachmentId)

    debugTotalAttachments.incrementAndGet()
    debugTotalBytes.addAndGet(details.totalBytes)

    if (BuildConfig.DEBUG) {
      Log.d(TAG, "Attachment restored: $details")
    }
  }

  private fun onProcessEnd() {
    if (debugAttachmentStartTime <= 0 || debugTotalAttachments.get() <= 0 || debugTotalBytes.get() <= 0) {
      Log.w(TAG, "Insufficient data to print debug stats.")
      return
    }

    val seconds: Double = (System.currentTimeMillis() - debugAttachmentStartTime).milliseconds.toDouble(DurationUnit.SECONDS)
    val bytesPerSecond: Long = (debugTotalBytes.get() / seconds).toLong()

    Log.w(TAG, "Restore Finished! TotalAttachments=$debugTotalAttachments, TotalBytes=$debugTotalBytes (${debugTotalBytes.get().bytes.toUnitString()}), Rate=$bytesPerSecond bytes/sec (${bytesPerSecond.bytes.toUnitString()}/sec)")
  }

  private class AttachmentProgressDetails(
    val startTimeMs: Long = 0,
    var networkFinishTime: Long = 0,
    var totalBytes: Long = 0
  ) {
    override fun toString(): String {
      if (startTimeMs == 0L || totalBytes == 0L) {
        return "N/A"
      }

      val networkSeconds: Double = (networkFinishTime - startTimeMs).milliseconds.toDouble(DurationUnit.SECONDS)
      val networkBytesPerSecond: Long = (totalBytes / networkSeconds).toLong()

      val diskSeconds: Double = (System.currentTimeMillis() - networkFinishTime).milliseconds.toDouble(DurationUnit.SECONDS)
      val diskBytesPerSecond: Long = (totalBytes / diskSeconds).toLong()

      return "Duration=${System.currentTimeMillis() - startTimeMs}ms, TotalBytes=$totalBytes (${totalBytes.bytes.toUnitString()}), NetworkRate=$networkBytesPerSecond bytes/sec (${networkBytesPerSecond.bytes.toUnitString()}/sec), DiskRate=$diskBytesPerSecond bytes/sec (${diskBytesPerSecond.bytes.toUnitString()}/sec)"
    }
  }

  //endregion Attachment Debug
}
