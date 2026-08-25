package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.greenrobot.eventbus.EventBus
import org.signal.core.models.database.StickerRecord
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.SqlUtil
import org.signal.core.util.StreamUtil
import org.signal.core.util.crypto.AttachmentSecret
import org.signal.core.util.crypto.ModernDecryptingPartInputStream
import org.signal.core.util.crypto.ModernEncryptingPartOutputStream
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.forEach
import org.signal.core.util.hasUnknownFields
import org.signal.core.util.insertInto
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.signal.core.util.readToList
import org.signal.core.util.readToMap
import org.signal.core.util.readToSet
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.glide.decryptableuri.DecryptableUri
import org.thoughtcrime.securesms.database.model.IncomingSticker
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.database.model.StickerPackKey
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.database.model.StickerPackSyncRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob
import org.thoughtcrime.securesms.stickers.BlessedPacks
import org.thoughtcrime.securesms.stickers.StickerPackInstallEvent
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.storage.SignalStickerPackRecord
import org.whispersystems.signalservice.api.storage.StorageId
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Stores sticker packs and the individual stickers within them.
 * Broken into two tables: one for the overall pack info, and one for the individual stickers within the pack.
 */
class StickerTables(
  context: Context?,
  databaseHelper: SignalDatabase?,
  private val attachmentSecret: AttachmentSecret
) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val TAG = tag(StickerTables::class.java)

    val CREATE_TABLES: Array<String> = arrayOf(Sticker.CREATE_TABLE, Pack.CREATE_TABLE)

    val CREATE_INDEXES: Array<String> = arrayOf(
      "CREATE INDEX IF NOT EXISTS sticker_pack_id_index ON ${Sticker.TABLE_NAME} (${Sticker.PACK_ID});",
      "CREATE INDEX IF NOT EXISTS sticker_sticker_id_index ON ${Sticker.TABLE_NAME} (${Sticker.STICKER_ID});"
    )

    const val DIRECTORY: String = "stickers"

    private val JOINED_TABLES = "${Sticker.TABLE_NAME} INNER JOIN ${Pack.TABLE_NAME} ON ${Sticker.TABLE_NAME}.${Sticker.PACK_ID} = ${Pack.TABLE_NAME}.${Pack.PACK_ID}"

    private val RECORD_PROJECTION = arrayOf(
      "${Sticker.TABLE_NAME}.${Sticker.ID} AS ${Sticker.ID}",
      "${Sticker.TABLE_NAME}.${Sticker.PACK_ID} AS ${Sticker.PACK_ID}",
      "${Pack.TABLE_NAME}.${Pack.PACK_KEY} AS ${Pack.PACK_KEY}",
      "${Pack.TABLE_NAME}.${Pack.PACK_TITLE} AS ${Pack.PACK_TITLE}",
      "${Pack.TABLE_NAME}.${Pack.PACK_AUTHOR} AS ${Pack.PACK_AUTHOR}",
      "${Sticker.TABLE_NAME}.${Sticker.STICKER_ID} AS ${Sticker.STICKER_ID}",
      "${Sticker.TABLE_NAME}.${Sticker.COVER} AS ${Sticker.COVER}",
      "${Pack.TABLE_NAME}.${Pack.POSITION} AS ${Pack.POSITION}",
      "${Sticker.TABLE_NAME}.${Sticker.EMOJI} AS ${Sticker.EMOJI}",
      "${Sticker.TABLE_NAME}.${Sticker.CONTENT_TYPE} AS ${Sticker.CONTENT_TYPE}",
      "${Sticker.TABLE_NAME}.${Sticker.LAST_USED} AS ${Sticker.LAST_USED}",
      "${Pack.TABLE_NAME}.${Pack.INSTALLED} AS ${Pack.INSTALLED}",
      "${Sticker.TABLE_NAME}.${Sticker.FILE_PATH} AS ${Sticker.FILE_PATH}",
      "${Sticker.TABLE_NAME}.${Sticker.FILE_LENGTH} AS ${Sticker.FILE_LENGTH}",
      "${Sticker.TABLE_NAME}.${Sticker.FILE_RANDOM} AS ${Sticker.FILE_RANDOM}"
    )
  }

  object Sticker {
    const val TABLE_NAME: String = "sticker"
    const val ID: String = "_id"
    const val PACK_ID: String = "pack_id"
    const val STICKER_ID = "sticker_id"
    const val EMOJI: String = "emoji"
    const val CONTENT_TYPE: String = "content_type"
    const val COVER: String = "cover"
    const val LAST_USED = "last_used"
    const val FILE_PATH: String = "file_path"
    const val FILE_LENGTH: String = "file_length"
    const val FILE_RANDOM: String = "file_random"

    const val CREATE_TABLE: String = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $PACK_ID TEXT NOT NULL REFERENCES ${Pack.TABLE_NAME} (${Pack.PACK_ID}) ON DELETE CASCADE,
        $STICKER_ID INTEGER,
        $COVER INTEGER,
        $EMOJI TEXT NOT NULL,
        $CONTENT_TYPE TEXT DEFAULT NULL,
        $LAST_USED INTEGER,
        $FILE_PATH TEXT NOT NULL,
        $FILE_LENGTH INTEGER,
        $FILE_RANDOM BLOB,
        UNIQUE($PACK_ID, $STICKER_ID, $COVER) ON CONFLICT IGNORE
      )
      """
  }

  /**
   * Pack-level details, one row per sticker pack. Individual stickers live in [StickerTables] and link
   * back here via [PACK_ID].
   */
  object Pack {
    const val TABLE_NAME: String = "sticker_pack"
    const val ID: String = "_id"
    const val PACK_ID: String = "pack_id"
    const val PACK_KEY: String = "pack_key"
    const val PACK_TITLE: String = "pack_title"
    const val PACK_AUTHOR: String = "pack_author"
    const val INSTALLED: String = "installed"
    const val POSITION: String = "position"
    const val STORAGE_SERVICE_ID: String = "storage_service_id"
    const val STORAGE_SERVICE_PROTO: String = "storage_service_proto"
    const val DELETED_TIMESTAMP_MS: String = "deleted_timestamp_ms"

    val CREATE_TABLE: String = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $PACK_ID TEXT NOT NULL UNIQUE,
        $PACK_KEY TEXT NOT NULL,
        $PACK_TITLE TEXT NOT NULL,
        $PACK_AUTHOR TEXT NOT NULL,
        $INSTALLED INTEGER,
        $POSITION INTEGER DEFAULT 0,
        $STORAGE_SERVICE_ID TEXT DEFAULT NULL,
        $STORAGE_SERVICE_PROTO TEXT DEFAULT NULL,
        $DELETED_TIMESTAMP_MS INTEGER DEFAULT 0
      )
      """
  }

  @Throws(IOException::class)
  fun insertSticker(sticker: IncomingSticker, dataStream: InputStream, notify: Boolean) {
    val fileInfo: FileInfo = saveStickerImage(dataStream)
    var becameInstalled = false

    writableDatabase.withinTransaction { db ->
      becameInstalled = upsertStickerPack(db, sticker)

      val values = contentValuesOf(
        Sticker.PACK_ID to sticker.packId,
        Sticker.STICKER_ID to sticker.stickerId,
        Sticker.EMOJI to sticker.emoji,
        Sticker.CONTENT_TYPE to sticker.contentType,
        Sticker.COVER to if (sticker.isCover) 1 else 0,
        Sticker.FILE_PATH to fileInfo.file.absolutePath,
        Sticker.FILE_LENGTH to fileInfo.length,
        Sticker.FILE_RANDOM to fileInfo.random
      )

      var updated = false
      if (sticker.isCover) {
        // Archive restore inserts cover rows without a sticker id, try to update first on a reduced uniqueness constraint
        updated = db
          .update(Sticker.TABLE_NAME)
          .values(values)
          .where("${Sticker.PACK_ID} = ? AND ${Sticker.COVER} = 1", sticker.packId)
          .run() > 0
      }

      if (!updated) {
        db
          .insertInto(Sticker.TABLE_NAME)
          .values(values)
          .run(SQLiteDatabase.CONFLICT_REPLACE)
      }
    }

    notifyStickerListeners()

    if (becameInstalled) {
      StorageSyncHelper.scheduleSyncForDataChange()
    }

    if (sticker.isCover) {
      notifyStickerPackListeners()

      if (sticker.isInstalled && notify) {
        broadcastInstallEvent(sticker.packId)
      }
    }
  }

  /**
   * Inserts a pack reference (its cover only) without any downloaded sticker data, used when restoring
   * from an archive. If the pack already exists, this is a no-op.
   */
  fun insertPackReference(packId: String, packKey: String) {
    writableDatabase.withinTransaction { db ->
      db
        .insertInto(Pack.TABLE_NAME)
        .values(
          Pack.PACK_ID to packId,
          Pack.PACK_KEY to packKey,
          Pack.PACK_TITLE to "",
          Pack.PACK_AUTHOR to "",
          Pack.INSTALLED to 1,
          Pack.POSITION to getNextPosition(db),
          Pack.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey())
        )
        .run(SQLiteDatabase.CONFLICT_IGNORE)

      db
        .insertInto(Sticker.TABLE_NAME)
        .values(
          Sticker.PACK_ID to packId,
          Sticker.COVER to 1,
          Sticker.EMOJI to "",
          Sticker.CONTENT_TYPE to "",
          Sticker.FILE_PATH to ""
        )
        .run(SQLiteDatabase.CONFLICT_IGNORE)
    }
  }

  fun getSticker(packId: String, stickerId: Int, isCover: Boolean): StickerRecord? {
    return readableDatabase
      .select(*RECORD_PROJECTION)
      .from(JOINED_TABLES)
      .where("${Sticker.TABLE_NAME}.${Sticker.PACK_ID} = ? AND ${Sticker.TABLE_NAME}.${Sticker.STICKER_ID} = ? AND ${Sticker.TABLE_NAME}.${Sticker.COVER} = ?", packId, stickerId.toString(), isCover.toInt())
      .run()
      .readToSingleObject { it.readStickerRecord() }
  }

  fun getStickerPack(packId: String): StickerPackRecord? {
    return readableDatabase
      .select(*RECORD_PROJECTION)
      .from(JOINED_TABLES)
      .where("${Pack.TABLE_NAME}.${Pack.PACK_ID} = ? AND ${Sticker.TABLE_NAME}.${Sticker.COVER} = 1", packId)
      .run()
      .readToSingleObject { it.readStickerPackRecord() }
  }

  fun getInstalledStickerPacks(): Cursor {
    return readableDatabase
      .select(*RECORD_PROJECTION)
      .from(JOINED_TABLES)
      .where("${Sticker.TABLE_NAME}.${Sticker.COVER} = 1 AND ${Pack.TABLE_NAME}.${Pack.INSTALLED} = 1")
      .orderBy("${Pack.TABLE_NAME}.${Pack.POSITION} ASC, ${Pack.TABLE_NAME}.${Pack.PACK_ID} ASC")
      .run()
  }

  fun getStickersByEmoji(emoji: String): Cursor {
    return readableDatabase
      .select(*RECORD_PROJECTION)
      .from(JOINED_TABLES)
      .where("${Sticker.TABLE_NAME}.${Sticker.EMOJI} LIKE ? AND ${Sticker.TABLE_NAME}.${Sticker.COVER} = 0", "%$emoji%")
      .run()
  }

  fun getAllStickerPacks(): Cursor {
    return getAllStickerPacks(null)
  }

  fun getAllStickerPacks(limit: String?): Cursor {
    return readableDatabase.query(
      JOINED_TABLES,
      RECORD_PROJECTION,
      "${Sticker.TABLE_NAME}.${Sticker.COVER} = 1",
      null,
      "${Sticker.TABLE_NAME}.${Sticker.PACK_ID}",
      null,
      "${Pack.TABLE_NAME}.${Pack.POSITION} ASC, ${Pack.TABLE_NAME}.${Pack.PACK_ID} ASC",
      limit
    )
  }

  fun getStickersForPack(packId: String): Cursor {
    return readableDatabase
      .select(*RECORD_PROJECTION)
      .from(JOINED_TABLES)
      .where("${Sticker.TABLE_NAME}.${Sticker.PACK_ID} = ? AND ${Sticker.TABLE_NAME}.${Sticker.COVER} = 0", packId)
      .orderBy("${Sticker.TABLE_NAME}.${Sticker.STICKER_ID} ASC")
      .run()
  }

  fun getRecentlyUsedStickers(limit: Int): Cursor {
    return readableDatabase
      .select(*RECORD_PROJECTION)
      .from(JOINED_TABLES)
      .where("${Sticker.TABLE_NAME}.${Sticker.LAST_USED} > 0 AND ${Sticker.TABLE_NAME}.${Sticker.COVER} = 0")
      .orderBy("${Sticker.TABLE_NAME}.${Sticker.LAST_USED} DESC")
      .limit(limit)
      .run()
  }

  fun getAllStickerFiles(): Set<String> {
    return readableDatabase
      .select(Sticker.FILE_PATH)
      .from(Sticker.TABLE_NAME)
      .run()
      .readToSet { it.requireNonNullString(Sticker.FILE_PATH) }
  }

  @Throws(IOException::class)
  fun getStickerStream(rowId: Long): InputStream? {
    return readableDatabase
      .select()
      .from(Sticker.TABLE_NAME)
      .where("${Sticker.ID} = ?", rowId)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          val path = cursor.requireString(Sticker.FILE_PATH)
          val random = cursor.requireBlob(Sticker.FILE_RANDOM)

          if (path != null && random != null) {
            ModernDecryptingPartInputStream.createFor(attachmentSecret, random, File(path), 0)
          } else {
            Log.w(TAG, "getStickerStream($rowId) - No sticker data")
            null
          }
        } else {
          Log.w(TAG, "getStickerStream($rowId) - Sticker not found")
          null
        }
      }
  }

  fun isPackInstalled(packId: String): Boolean {
    return getStickerPack(packId)?.isInstalled ?: false
  }

  fun isPackAvailableAsReference(packId: String): Boolean {
    return readableDatabase
      .exists(Sticker.TABLE_NAME)
      .where("${Sticker.PACK_ID} = ? AND ${Sticker.COVER} = 1", packId)
      .run()
  }

  fun updateStickerLastUsedTime(rowId: Long, lastUsed: Long) {
    writableDatabase
      .update(Sticker.TABLE_NAME)
      .values(Sticker.LAST_USED to lastUsed)
      .where("${Sticker.ID} = ?", rowId)
      .run()

    notifyStickerListeners()
    notifyStickerPackListeners()
  }

  fun markPackAsInstalled(packId: String, notify: Boolean) {
    val transitioned = updatePackInstalled(
      db = databaseHelper.signalWritableDatabase,
      packId = packId,
      installed = true,
      notify = notify
    )

    if (transitioned) {
      StorageSyncHelper.scheduleSyncForDataChange()
    }

    notifyStickerPackListeners()
  }

  fun deleteOrphanedPacks() {
    var performedDelete = false

    writableDatabase.withinTransaction { db ->
      db.rawQuery(
        """
        SELECT ${Pack.PACK_ID}
        FROM ${Pack.TABLE_NAME}
        WHERE
          ${Pack.INSTALLED} = 0 AND
          ${Pack.STORAGE_SERVICE_ID} IS NULL AND
          ${Pack.PACK_ID} NOT IN (
            SELECT DISTINCT ${AttachmentTable.STICKER_PACK_ID}
            FROM ${AttachmentTable.TABLE_NAME}
            WHERE ${AttachmentTable.STICKER_PACK_ID} NOT NULL
          )
        """,
        null
      ).forEach { cursor ->
        val packId = cursor.getString(cursor.getColumnIndexOrThrow(Pack.PACK_ID))

        if (!BlessedPacks.contains(packId)) {
          deletePack(db, packId)
          performedDelete = true
        }
      }
    }

    if (performedDelete) {
      notifyStickerPackListeners()
      notifyStickerListeners()
    }
  }

  fun uninstallPack(packId: String) {
    uninstallPacks(setOf(StickerPackId(packId)))
  }

  fun uninstallPacks(packIds: Set<StickerPackId>) {
    var transitioned = false

    writableDatabase.withinTransaction { db ->
      packIds.forEach { packId ->
        transitioned = updatePackInstalled(db = db, packId = packId.value, installed = false, notify = false) || transitioned
        deleteStickersInPackExceptCover(db, packId.value)
      }
    }

    if (transitioned) {
      StorageSyncHelper.scheduleSyncForDataChange()
    }

    notifyStickerPackListeners()
    notifyStickerListeners()
  }

  /**
   * Rewrites positions so packs display in [packsInOrder] order. Packs render in ascending
   * position order, so the first pack gets position 0.
   */
  fun updatePackPositions(packsInOrder: List<StickerPackRecord>) {
    writableDatabase.withinTransaction { db ->
      for ((i, pack) in packsInOrder.withIndex()) {
        db.update(Pack.TABLE_NAME)
          .values(
            Pack.POSITION to i,
            Pack.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey())
          )
          .where("${Pack.PACK_ID} = ?", pack.packId)
          .run()
      }
    }

    StorageSyncHelper.scheduleSyncForDataChange()
    notifyStickerPackListeners()
  }

  /**
   * Returns the sync-relevant fields for the pack with the given id, if a row for the pack exists.
   */
  fun getPackForStorageSync(packId: StickerPackId): StickerPackSyncRecord? {
    return getPackForStorageSync(SqlUtil.buildQuery("${Pack.PACK_ID} = ?", packId.value))
  }

  /**
   * Returns the sync-relevant fields for a single pack matching the query, if one exists.
   */
  fun getPackForStorageSync(query: SqlUtil.Query): StickerPackSyncRecord? {
    return readableDatabase
      .select()
      .from(Pack.TABLE_NAME)
      .where(query.where, query.whereArgs)
      .run()
      .readToSingleObject { cursor ->
        StickerPackSyncRecord(
          packId = StickerPackId(cursor.requireNonNullString(Pack.PACK_ID)),
          packKey = StickerPackKey(cursor.requireNonNullString(Pack.PACK_KEY)),
          position = cursor.requireInt(Pack.POSITION),
          installed = cursor.requireBoolean(Pack.INSTALLED),
          deletedTimestampMs = cursor.requireLong(Pack.DELETED_TIMESTAMP_MS),
          storageServiceId = cursor.requireString(Pack.STORAGE_SERVICE_ID)?.let { StorageId.forStickerPack(Base64.decodeOrThrow(it)) },
          storageServiceProto = Base64.decodeOrNull(cursor.requireString(Pack.STORAGE_SERVICE_PROTO))
        )
      }
  }

  /**
   * Returns the storage ids of all packs that participate in storage sync (installed packs and tombstones).
   */
  fun getStorageSyncIds(): List<StorageId> {
    return readableDatabase
      .select(Pack.STORAGE_SERVICE_ID)
      .from(Pack.TABLE_NAME)
      .where("${Pack.STORAGE_SERVICE_ID} NOT NULL")
      .run()
      .readToList { cursor ->
        StorageId.forStickerPack(Base64.decodeOrThrow(cursor.requireNonNullString(Pack.STORAGE_SERVICE_ID)))
      }
  }

  /**
   * Maps pack ids to storage ids for all packs that participate in storage sync.
   */
  fun getStorageSyncIdsMap(): Map<StickerPackId, StorageId> {
    return readableDatabase
      .select(Pack.PACK_ID, Pack.STORAGE_SERVICE_ID)
      .from(Pack.TABLE_NAME)
      .where("${Pack.STORAGE_SERVICE_ID} NOT NULL")
      .run()
      .readToMap { cursor ->
        val packId = StickerPackId(cursor.requireNonNullString(Pack.PACK_ID))
        val key = Base64.decodeOrThrow(cursor.requireNonNullString(Pack.STORAGE_SERVICE_ID))
        packId to StorageId.forStickerPack(key)
      }
  }

  /**
   * Saves the new storage id for a sticker pack.
   */
  fun applyStorageIdUpdate(packId: StickerPackId, storageId: StorageId) {
    applyStorageIdUpdates(mapOf(packId to storageId))
  }

  /**
   * Saves the new storage ids for all the sticker packs in the map.
   */
  fun applyStorageIdUpdates(storageIds: Map<StickerPackId, StorageId>) {
    writableDatabase.withinTransaction { db ->
      storageIds.forEach { (packId, storageId) ->
        db.update(Pack.TABLE_NAME)
          .values(Pack.STORAGE_SERVICE_ID to Base64.encodeWithPadding(storageId.raw))
          .where("${Pack.PACK_ID} = ?", packId.value)
          .run()
      }
    }
  }

  /**
   * Rotates the storage ids for the given packs. Assumption is that [StorageSyncHelper.scheduleSyncForDataChange] will be called after.
   */
  fun markNeedsSync(packIds: Collection<StickerPackId>) {
    writableDatabase.withinTransaction { db ->
      packIds.forEach { packId ->
        db.update(Pack.TABLE_NAME)
          .values(Pack.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey()))
          .where("${Pack.PACK_ID} = ?", packId.value)
          .run()
      }
    }
  }

  /**
   * Applies a remote sticker pack record to the local store, inserting a row if one doesn't exist yet.
   * Installs the pack's stickers if the remote record is not deleted.
   */
  fun insertStickerPackFromStorageSync(record: SignalStickerPackRecord) {
    applyStickerPackFromStorageSync(record)
  }

  /**
   * Updates an existing local pack with the details of the remote sticker pack record.
   */
  fun updateStickerPackFromStorageSync(record: SignalStickerPackRecord) {
    applyStickerPackFromStorageSync(record)
  }

  /**
   * Removes storage ids from packs that were deleted before [deletedBefore].
   */
  fun removeStorageIdsFromOldDeletedPacks(deletedBefore: Long): Int {
    return writableDatabase
      .update(Pack.TABLE_NAME)
      .values(Pack.STORAGE_SERVICE_ID to null)
      .where("${Pack.STORAGE_SERVICE_ID} NOT NULL AND ${Pack.DELETED_TIMESTAMP_MS} > 0 AND ${Pack.DELETED_TIMESTAMP_MS} < ?", deletedBefore)
      .run()
  }

  /**
   * Removes storage ids of packs that are deleted locally and no longer present in the remote manifest.
   */
  fun removeStorageIdsFromLocalOnlyDeletedPacks(storageIds: Collection<StorageId>): Int {
    var updated = 0

    SqlUtil.buildCollectionQuery(Pack.STORAGE_SERVICE_ID, storageIds.map { Base64.encodeWithPadding(it.raw) }, "${Pack.DELETED_TIMESTAMP_MS} > 0 AND")
      .forEach { query ->
        updated += writableDatabase
          .update(Pack.TABLE_NAME)
          .values(Pack.STORAGE_SERVICE_ID to null)
          .where(query.where, *query.whereArgs)
          .run()
      }

    return updated
  }

  private fun applyStickerPackFromStorageSync(record: SignalStickerPackRecord) {
    val packId = Hex.toStringCondensed(record.proto.packId.toByteArray())
    val packKey = Hex.toStringCondensed(record.proto.packKey.toByteArray())
    val deleted = record.proto.deletedAtTimestamp > 0
    val storageServiceProto = if (record.proto.hasUnknownFields()) Base64.encodeWithPadding(record.serializedUnknowns!!) else null

    var wasInstalled = false

    writableDatabase.withinTransaction { db ->
      wasInstalled = getPackForStorageSync(StickerPackId(packId))?.installed ?: false

      val values = contentValuesOf(
        Pack.INSTALLED to (!deleted).toInt(),
        Pack.POSITION to if (deleted) 0 else record.proto.position,
        Pack.DELETED_TIMESTAMP_MS to record.proto.deletedAtTimestamp,
        Pack.STORAGE_SERVICE_ID to Base64.encodeWithPadding(record.id.raw),
        Pack.STORAGE_SERVICE_PROTO to storageServiceProto
      )

      if (packKey.isNotEmpty()) {
        values.put(Pack.PACK_KEY, packKey)
      }

      val updated = db
        .update(Pack.TABLE_NAME)
        .values(values)
        .where("${Pack.PACK_ID} = ?", packId)
        .run()

      if (updated == 0) {
        values.put(Pack.PACK_ID, packId)
        values.put(Pack.PACK_KEY, packKey)
        values.put(Pack.PACK_TITLE, "")
        values.put(Pack.PACK_AUTHOR, "")

        db
          .insertInto(Pack.TABLE_NAME)
          .values(values)
          .run(SQLiteDatabase.CONFLICT_IGNORE)
      }

      if (deleted) {
        deleteStickersInPackExceptCover(db, packId)
      }
    }

    if (!deleted && !wasInstalled) {
      AppDependencies.jobManager.add(StickerPackDownloadJob.forInstall(packId, packKey, false))
    }

    notifyStickerPackListeners()
    notifyStickerListeners()
  }

  /**
   * The position of the most recently installed pack, plus one. Packs render in ascending position
   * order, so new installs are appended to the end of the list.
   */
  private fun getNextPosition(db: SQLiteDatabase): Int {
    return db
      .select("IFNULL(MAX(${Pack.POSITION}) + 1, 0)")
      .from(Pack.TABLE_NAME)
      .where("${Pack.INSTALLED} = 1")
      .run()
      .readToSingleInt(0)
  }

  /**
   * @return True if the pack transitioned into the installed state, otherwise false.
   */
  private fun upsertStickerPack(db: SQLiteDatabase, sticker: IncomingSticker): Boolean {
    val existing = getPackForStorageSync(StickerPackId(sticker.packId))
    val becomingInstalled = sticker.isInstalled && (existing == null || !existing.installed)

    val values = contentValuesOf(
      Pack.PACK_ID to sticker.packId,
      Pack.PACK_KEY to sticker.packKey,
      Pack.PACK_TITLE to sticker.packTitle,
      Pack.PACK_AUTHOR to sticker.packAuthor,
      Pack.INSTALLED to if (sticker.isInstalled) 1 else 0
    )

    if (becomingInstalled) {
      values.put(Pack.POSITION, getNextPosition(db))
      values.put(Pack.DELETED_TIMESTAMP_MS, 0)
      values.put(Pack.STORAGE_SERVICE_ID, Base64.encodeWithPadding(StorageSyncHelper.generateKey()))
    }

    val updated = db
      .update(Pack.TABLE_NAME)
      .values(values)
      .where("${Pack.PACK_ID} = ?", sticker.packId)
      .run()

    if (updated == 0) {
      db
        .insertInto(Pack.TABLE_NAME)
        .values(values)
        .run(SQLiteDatabase.CONFLICT_IGNORE)
    }

    return becomingInstalled
  }

  /**
   * @return True if the installed state actually changed, otherwise false.
   */
  private fun updatePackInstalled(db: SQLiteDatabase, packId: String, installed: Boolean, notify: Boolean): Boolean {
    val existing = getPackForStorageSync(StickerPackId(packId))

    if (existing != null && existing.installed == installed) {
      return false
    }

    val values = if (installed) {
      contentValuesOf(
        Pack.INSTALLED to 1,
        Pack.POSITION to getNextPosition(db),
        Pack.DELETED_TIMESTAMP_MS to 0,
        Pack.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey())
      )
    } else {
      contentValuesOf(
        Pack.INSTALLED to 0,
        Pack.POSITION to 0,
        Pack.DELETED_TIMESTAMP_MS to System.currentTimeMillis(),
        Pack.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey())
      )
    }

    val updated = db.update(Pack.TABLE_NAME)
      .values(values)
      .where("${Pack.PACK_ID} = ?", packId)
      .run()

    if (updated == 0) {
      return false
    }

    if (installed && notify) {
      broadcastInstallEvent(packId)
    }

    return true
  }

  @Throws(IOException::class)
  private fun saveStickerImage(inputStream: InputStream): FileInfo {
    val partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
    val file = File.createTempFile("sticker", ".mms", partsDirectory)
    val out = ModernEncryptingPartOutputStream.createFor(attachmentSecret, file, false)
    val length = StreamUtil.copy(inputStream, out.second)

    return FileInfo(file, length, out.first!!)
  }

  private fun deleteSticker(db: SQLiteDatabase, rowId: Long, filePath: String?) {
    db.delete(Sticker.TABLE_NAME)
      .where("${Sticker.ID} = ?", rowId)
      .run()

    if (filePath.isNotNullOrBlank()) {
      File(filePath).delete()
    }
  }

  private fun deletePack(db: SQLiteDatabase, packId: String) {
    deleteStickersInPack(db, packId)

    db.delete(Pack.TABLE_NAME)
      .where("${Pack.PACK_ID} = ?", packId)
      .run()
  }

  private fun deleteStickersInPack(database: SQLiteDatabase, packId: String) {
    database.withinTransaction { db ->
      db.select(Sticker.ID, Sticker.FILE_PATH)
        .from(Sticker.TABLE_NAME)
        .where("${Sticker.PACK_ID} = ?", packId)
        .run()
        .forEach { cursor ->
          val rowId = cursor.requireLong(Sticker.ID)
          val filePath = cursor.requireString(Sticker.FILE_PATH)

          deleteSticker(db, rowId, filePath)
        }

      db.delete(Sticker.TABLE_NAME)
        .where("${Sticker.PACK_ID} = ?", packId)
        .run()
    }
  }

  private fun deleteStickersInPackExceptCover(database: SQLiteDatabase, packId: String) {
    database.withinTransaction { db ->
      db.select(Sticker.ID, Sticker.FILE_PATH)
        .from(Sticker.TABLE_NAME)
        .where("${Sticker.PACK_ID} = ? AND ${Sticker.COVER} = 0", packId)
        .run()
        .forEach { cursor ->
          val rowId = cursor.requireLong(Sticker.ID)
          val filePath = cursor.requireString(Sticker.FILE_PATH)

          deleteSticker(db, rowId, filePath)
        }
    }
  }

  private fun broadcastInstallEvent(packId: String) {
    val pack = getStickerPack(packId)

    if (pack != null) {
      EventBus.getDefault().postSticky(StickerPackInstallEvent(DecryptableUri(pack.cover.uri)))
    }
  }

  private fun Cursor.readStickerRecord(): StickerRecord {
    return StickerRecordReader(this).getCurrent()
  }

  private fun Cursor.readStickerPackRecord(): StickerPackRecord {
    return StickerPackRecordReader(this).getCurrent()
  }

  private class FileInfo(
    val file: File,
    val length: Long,
    val random: ByteArray
  )

  class StickerRecordReader(private val cursor: Cursor) : Closeable {

    fun getNext(): StickerRecord? {
      if (!cursor.moveToNext()) {
        return null
      }

      return getCurrent()
    }

    fun getCurrent(): StickerRecord {
      return StickerRecord(
        rowId = cursor.requireLong(Sticker.ID),
        packId = cursor.requireNonNullString(Sticker.PACK_ID),
        packKey = cursor.requireNonNullString(Pack.PACK_KEY),
        stickerId = cursor.requireInt(Sticker.STICKER_ID),
        emoji = cursor.requireNonNullString(Sticker.EMOJI),
        contentType = cursor.requireString(Sticker.CONTENT_TYPE) ?: MediaUtil.IMAGE_WEBP,
        size = cursor.requireLong(Sticker.FILE_LENGTH),
        isCover = cursor.requireBoolean(Sticker.COVER)
      )
    }

    override fun close() {
      cursor.close()
    }
  }

  class StickerPackRecordReader(private val cursor: Cursor) : Closeable, Iterable<StickerPackRecord> {

    fun getNext(): StickerPackRecord? {
      if (!cursor.moveToNext()) {
        return null
      }

      return getCurrent()
    }

    fun getCurrent(): StickerPackRecord {
      val cover = StickerRecordReader(cursor).getCurrent()

      return StickerPackRecord(
        packId = cursor.requireNonNullString(Sticker.PACK_ID),
        packKey = cursor.requireNonNullString(Pack.PACK_KEY),
        title = cursor.requireNonNullString(Pack.PACK_TITLE),
        author = cursor.requireNonNullString(Pack.PACK_AUTHOR),
        cover = cover,
        isInstalled = cursor.requireBoolean(Pack.INSTALLED)
      )
    }

    fun asSequence(): Sequence<StickerPackRecord> = sequence {
      while (getNext() != null) {
        yield(getCurrent())
      }
    }

    override fun close() {
      cursor.close()
    }

    override fun iterator(): Iterator<StickerPackRecord> {
      return ReaderIterator()
    }

    private inner class ReaderIterator : Iterator<StickerPackRecord> {
      override fun hasNext(): Boolean {
        return cursor.count != 0 && !cursor.isLast
      }

      override fun next(): StickerPackRecord {
        return getNext() ?: throw NoSuchElementException()
      }
    }
  }
}
