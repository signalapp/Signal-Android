/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import androidx.annotation.Discouraged
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.requireNonNullString
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTable
import org.thoughtcrime.securesms.database.StickerTable.StickerPackRecordReader
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.database.model.StickerPackKey
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.jobs.MultiDeviceStickerPackOperationJob
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Handles the retrieval and modification of sticker pack data.
 */
object StickerManagementRepository {
  private val jobManager: JobManager = AppDependencies.jobManager
  private val databaseObserver: DatabaseObserver = AppDependencies.databaseObserver
  private val stickersDbTable: StickerTable = SignalDatabase.stickers
  private val attachmentsDbTable: AttachmentTable = SignalDatabase.attachments
  private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

  @Discouraged("For Java use only. In Kotlin, use the getStickerPacks() overload that returns a Flow instead.")
  @WorkerThread
  fun getStickerPacks(callback: Callback<StickerPacksResult>) {
    coroutineScope.launch {
      callback.onComplete(loadStickerPacks())
    }
  }

  /**
   * Emits the sticker packs along with any updates.
   */
  fun getStickerPacks(): Flow<StickerPacksResult> = callbackFlow {
    trySend(loadStickerPacks())

    val stickersDbObserver = DatabaseObserver.Observer {
      launch {
        deleteOrphanedStickerPacks()
        trySend(loadStickerPacks())
      }
    }

    databaseObserver.registerStickerPackObserver(stickersDbObserver)
    awaitClose {
      databaseObserver.unregisterObserver(stickersDbObserver)
    }
  }

  private suspend fun loadStickerPacks(): StickerPacksResult = withContext(Dispatchers.IO) {
    StickerPackRecordReader(stickersDbTable.getAllStickerPacks()).use { reader ->
      val installedPacks = mutableListOf<StickerPackRecord>()
      val availablePacks = mutableListOf<StickerPackRecord>()
      val blessedPacks = mutableListOf<StickerPackRecord>()
      val sortOrderById = mutableMapOf<StickerPackId, Int>()

      reader.asSequence().forEachIndexed { index, record ->
        when {
          record.isInstalled -> installedPacks.add(record)
          BlessedPacks.contains(record.packId) -> blessedPacks.add(record)
          else -> availablePacks.add(record)
        }
        sortOrderById[StickerPackId(record.packId)] = index
      }

      StickerPacksResult(
        installedPacks = installedPacks,
        availablePacks = availablePacks,
        blessedPacks = blessedPacks,
        sortOrderByPackId = sortOrderById
      )
    }
  }

  @Discouraged("For Java use only. In Kotlin, use deleteOrphanedStickerPacks() instead.")
  fun deleteOrphanedStickerPacksAsync() {
    coroutineScope.launch {
      deleteOrphanedStickerPacks()
    }
  }

  suspend fun deleteOrphanedStickerPacks() = withContext(Dispatchers.IO) {
    stickersDbTable.deleteOrphanedPacks()
  }

  fun fetchUnretrievedReferencePacks() {
    attachmentsDbTable.getUnavailableStickerPacks().use { cursor ->
      while (cursor.moveToNext()) {
        val packId: String = cursor.requireNonNullString(AttachmentTable.STICKER_PACK_ID)
        val packKey: String = cursor.requireNonNullString(AttachmentTable.STICKER_PACK_KEY)
        jobManager.add(StickerPackDownloadJob.forReference(packId, packKey))
      }
    }
  }

  fun installStickerPackAsync(packId: String, packKey: String, notify: Boolean) {
    coroutineScope.launch {
      installStickerPack(StickerPackId(packId), StickerPackKey(packKey), notify)
    }
  }

  suspend fun installStickerPack(packId: StickerPackId, packKey: StickerPackKey, notify: Boolean) = withContext(Dispatchers.IO) {
    if (stickersDbTable.isPackAvailableAsReference(packId.value)) {
      stickersDbTable.markPackAsInstalled(packId.value, notify)
    }

    jobManager.add(StickerPackDownloadJob.forInstall(packId.value, packKey.value, notify))

    if (SignalStore.account.hasLinkedDevices) {
      jobManager.add(MultiDeviceStickerPackOperationJob(packId.value, packKey.value, MultiDeviceStickerPackOperationJob.Type.INSTALL))
    }
  }

  @Discouraged("For Java use only. In Kotlin, use uninstallStickerPack() instead.")
  fun uninstallStickerPackAsync(packId: String, packKey: String) {
    coroutineScope.launch {
      uninstallStickerPack(StickerPackId(packId), StickerPackKey(packKey))
    }
  }

  suspend fun uninstallStickerPack(packId: StickerPackId, packKey: StickerPackKey) = withContext(Dispatchers.IO) {
    stickersDbTable.uninstallPack(packId.value)

    if (SignalStore.account.hasLinkedDevices) {
      AppDependencies.jobManager.add(MultiDeviceStickerPackOperationJob(packId.value, packKey.value, MultiDeviceStickerPackOperationJob.Type.REMOVE))
    }
  }

  @Discouraged("For Java use only. In Kotlin, use setStickerPackOrder() instead.")
  fun setStickerPacksOrderAsync(packsInOrder: List<StickerPackRecord>) {
    coroutineScope.launch {
      setStickerPacksOrder(packsInOrder)
    }
  }

  suspend fun setStickerPacksOrder(packsInOrder: List<StickerPackRecord>) = withContext(Dispatchers.IO) {
    stickersDbTable.updatePackOrder(packsInOrder)
  }

  interface Callback<T> {
    fun onComplete(result: T)
  }
}

data class StickerPacksResult(
  val installedPacks: List<StickerPackRecord>,
  val availablePacks: List<StickerPackRecord>,
  val blessedPacks: List<StickerPackRecord>,
  val sortOrderByPackId: Map<StickerPackId, Int>
)
