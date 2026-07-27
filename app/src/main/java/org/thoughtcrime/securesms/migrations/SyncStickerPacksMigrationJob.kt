package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTables
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Marks all installed sticker packs as needing to be synced for storage service.
 */
internal class SyncStickerPacksMigrationJob(parameters: Parameters = Parameters.Builder().build()) : MigrationJob(parameters) {
  companion object {
    const val KEY = "SyncStickerPacksMigrationJob"

    private val TAG = Log.tag(SyncStickerPacksMigrationJob::class)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (SignalStore.account.aci == null) {
      Log.w(TAG, "Self not available yet.")
      return
    }

    val packIds = SignalDatabase.stickers.getInstalledPackIds()

    SignalDatabase.stickers.markNeedsSync(packIds)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  private fun StickerTables.getInstalledPackIds(): List<StickerPackId> {
    return readableDatabase
      .select(StickerTables.Pack.PACK_ID)
      .from(StickerTables.Pack.TABLE_NAME)
      .where("${StickerTables.Pack.INSTALLED} = 1")
      .run()
      .readToList { cursor -> StickerPackId(cursor.requireNonNullString(StickerTables.Pack.PACK_ID)) }
  }

  class Factory : Job.Factory<SyncStickerPacksMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SyncStickerPacksMigrationJob {
      return SyncStickerPacksMigrationJob(parameters)
    }
  }
}
