package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.greenrobot.eventbus.EventBus
import org.signal.core.models.database.FavoriteStickerSyncRecord
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
import org.thoughtcrime.securesms.database.StickerTables.Pack.PACK_ID
import org.thoughtcrime.securesms.database.model.IncomingSticker
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.database.model.StickerPackKey
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.database.model.StickerPackSyncRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.StickerDownloadJob
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob
import org.thoughtcrime.securesms.stickers.BlessedPacks
import org.thoughtcrime.securesms.stickers.StickerPackInstallEvent
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.storage.SignalFavoriteStickerRecord
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

    const val MAX_FAVORITES = 500

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
      "${Sticker.TABLE_NAME}.${Sticker.FILE_RANDOM} AS ${Sticker.FILE_RANDOM}",
      "${Sticker.TABLE_NAME}.${Sticker.FAVORITED_AT} AS ${Sticker.FAVORITED_AT}"
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
    const val FAVORITED_AT: String = "favorited_at"
    const val UNFAVORITED_AT: String = "unfavorited_at"
    const val STORAGE_SERVICE_ID: String = "storage_service_id"
    const val STORAGE_SERVICE_PROTO: String = "storage_service_proto"

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
        $FAVORITED_AT INTEGER DEFAULT 0,
        $UNFAVORITED_AT INTEGER DEFAULT 0,
        $STORAGE_SERVICE_ID TEXT DEFAULT NULL,
        $STORAGE_SERVICE_PROTO TEXT DEFAULT NULL,
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
  fun insertSticker(sticker: IncomingSticker, dataStream: InputStream, notify: Boolean, updatePack: Boolean = true) {
    val fileInfo: FileInfo = saveStickerImage(dataStream)
    var becameInstalled = false

    val existingFile = writableDatabase.withinTransaction { db ->
      if (updatePack) {
        becameInstalled = upsertStickerPack(db, sticker)
      }

      val values = contentValuesOf(
        Sticker.PACK_ID to sticker.packId,
        Sticker.STICKER_ID to sticker.stickerId,
        Sticker.EMOJI to (sticker.emoji ?: ""),
        Sticker.CONTENT_TYPE to sticker.contentType,
        Sticker.COVER to if (sticker.isCover) 1 else 0,
        Sticker.FILE_PATH to fileInfo.file.absolutePath,
        Sticker.FILE_LENGTH to fileInfo.length,
        Sticker.FILE_RANDOM to fileInfo.random
      )

      val existingFile = db
        .select(Sticker.FILE_PATH)
        .from(Sticker.TABLE_NAME)
        .where("${Sticker.PACK_ID} = ? AND ${Sticker.STICKER_ID} = ? AND ${Sticker.COVER} = ?", sticker.packId, sticker.stickerId, sticker.isCover.toInt())
        .run()
        .readToSingleObject { it.requireNonNullString(Sticker.FILE_PATH) }

      val updated = if (sticker.isCover) {
        // Archive restore inserts cover rows without a sticker id, try to update first on a reduced uniqueness constraint
        db
          .update(Sticker.TABLE_NAME)
          .values(values)
          .where("${Sticker.PACK_ID} = ? AND ${Sticker.COVER} = 1", sticker.packId)
          .run() > 0
      } else {
        // Favorited and unfavorited stickers can be retained across uninstalls, so update in place to keep their favorite state
        db
          .update(Sticker.TABLE_NAME)
          .values(values)
          .where("${Sticker.PACK_ID} = ? AND ${Sticker.STICKER_ID} = ? AND ${Sticker.COVER} = 0 AND ${Sticker.STORAGE_SERVICE_ID} NOT NULL", sticker.packId, sticker.stickerId)
          .run() > 0
      }

      if (!updated) {
        db
          .insertInto(Sticker.TABLE_NAME)
          .values(values)
          .run(SQLiteDatabase.CONFLICT_REPLACE)
      }

      existingFile
    }

    if (existingFile.isNotNullOrBlank()) {
      Log.i(TAG, "Deleting existing file for sticker.")
      File(existingFile).delete()
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

      val hasCover = db
        .exists(Sticker.TABLE_NAME)
        .where("${Sticker.PACK_ID} = ? AND ${Sticker.COVER} = 1", packId)
        .run()

      // The cover goes in without a sticker id, which UNIQUE(pack_id, sticker_id, cover) cannot
      // dedupe, so a pack that already has a cover would otherwise end up with two.
      if (!hasCover) {
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

  /**
   * Grouped by pack, because a pack can end up with more than one cover row: an archive restore
   * inserts one without a sticker id, and SQLite's UNIQUE treats those nulls as distinct.
   */
  fun getInstalledStickerPacks(): Cursor {
    return readableDatabase.query(
      JOINED_TABLES,
      RECORD_PROJECTION,
      "${Sticker.TABLE_NAME}.${Sticker.COVER} = 1 AND ${Pack.TABLE_NAME}.${Pack.INSTALLED} = 1",
      null,
      "${Sticker.TABLE_NAME}.${Sticker.PACK_ID}",
      null,
      "${Pack.TABLE_NAME}.${Pack.POSITION} ASC, ${Pack.TABLE_NAME}.${Pack.PACK_ID} ASC",
      null
    )
  }

  fun getStickersByEmoji(emoji: String): Cursor {
    return readableDatabase
      .select(*RECORD_PROJECTION)
      .from(JOINED_TABLES)
      .where("${Sticker.TABLE_NAME}.${Sticker.EMOJI} LIKE ? AND ${Sticker.TABLE_NAME}.${Sticker.COVER} = 0 AND (${Pack.TABLE_NAME}.${Pack.INSTALLED} = 1 OR ${Sticker.TABLE_NAME}.${Sticker.FAVORITED_AT} > 0)", "%$emoji%")
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

  /**
   * Returns the IDs of the non-cover stickers in the pack whose image file is still on disk.
   */
  fun getDownloadedStickerIds(packId: String): Set<Int> {
    return readableDatabase
      .select(Sticker.STICKER_ID, Sticker.FILE_PATH)
      .from(Sticker.TABLE_NAME)
      .where("${Sticker.PACK_ID} = ? AND ${Sticker.COVER} = 0", packId)
      .run()
      .readToList { it.requireInt(Sticker.STICKER_ID) to it.requireNonNullString(Sticker.FILE_PATH) }
      .filter { (_, filePath) -> File(filePath).exists() }
      .map { (stickerId, _) -> stickerId }
      .toSet()
  }

  fun getRecentlyUsedStickers(limit: Int): Cursor {
    return readableDatabase
      .select(*RECORD_PROJECTION)
      .from(JOINED_TABLES)
      .where("${Sticker.TABLE_NAME}.${Sticker.LAST_USED} > 0 AND ${Sticker.TABLE_NAME}.${Sticker.COVER} = 0 AND (${Pack.TABLE_NAME}.${Pack.INSTALLED} = 1 OR ${Sticker.TABLE_NAME}.${Sticker.FAVORITED_AT} > 0)")
      .orderBy("${Sticker.TABLE_NAME}.${Sticker.LAST_USED} DESC")
      .limit(limit)
      .run()
  }

  fun getFavoriteStickers(): Cursor {
    return readableDatabase
      .select(*RECORD_PROJECTION)
      .from(JOINED_TABLES)
      .where("${Sticker.TABLE_NAME}.${Sticker.FAVORITED_AT} > 0 AND ${Sticker.TABLE_NAME}.${Sticker.COVER} = 0 AND ${Sticker.TABLE_NAME}.${Sticker.FILE_PATH} != ''")
      .orderBy("${Sticker.TABLE_NAME}.${Sticker.FAVORITED_AT} DESC")
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

  fun updateStickerLastUsedTime(packId: String, stickerId: Int, lastUsed: Long) {
    writableDatabase
      .update(Sticker.TABLE_NAME)
      .values(Sticker.LAST_USED to lastUsed)
      .where("${Sticker.PACK_ID} = ? AND ${Sticker.STICKER_ID} = ? AND ${Sticker.COVER} = 0", packId, stickerId)
      .run()

    notifyStickerListeners()
    notifyStickerPackListeners()
  }

  fun clearRecentlyUsedStickers() {
    writableDatabase
      .update(Sticker.TABLE_NAME)
      .values(Sticker.LAST_USED to 0)
      .where("${Sticker.LAST_USED} > 0 AND ${Sticker.COVER} = 0")
      .run()

    notifyStickerListeners()
    notifyStickerPackListeners()
  }

  fun getFavoriteCount(): Int {
    return readableDatabase
      .select("COUNT(*)")
      .from(Sticker.TABLE_NAME)
      .where("${Sticker.FAVORITED_AT} > 0 AND ${Sticker.COVER} = 0")
      .run()
      .readToSingleInt(0)
  }

  /**
   * Unfavorites however many stickers necessary to have at most [limit] stickers
   */
  fun unfavoriteNewestOverLimit(limit: Int): Int {
    val unfavoritedCount = writableDatabase.withinTransaction { db ->
      val overLimit = db
        .select("COUNT(*)")
        .from(Sticker.TABLE_NAME)
        .where("${Sticker.FAVORITED_AT} > 0 AND ${Sticker.COVER} = 0")
        .run()
        .readToSingleInt(0) - limit

      if (overLimit <= 0) {
        return@withinTransaction 0
      }

      val rowIds = db
        .select(Sticker.ID)
        .from(Sticker.TABLE_NAME)
        .where("${Sticker.FAVORITED_AT} > 0 AND ${Sticker.COVER} = 0")
        .orderBy("${Sticker.FAVORITED_AT} DESC, ${Sticker.ID} DESC")
        .limit(overLimit)
        .run()
        .readToList { it.requireLong(Sticker.ID) }

      rowIds.forEach { rowId ->
        db.update(Sticker.TABLE_NAME)
          .values(
            Sticker.FAVORITED_AT to 0,
            Sticker.UNFAVORITED_AT to System.currentTimeMillis(),
            Sticker.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey())
          )
          .where("${Sticker.ID} = ?", rowId)
          .run()
      }

      rowIds.size
    }

    if (unfavoritedCount > 0) {
      notifyStickerListeners()
    }

    return unfavoritedCount
  }

  /**
   * Whether the sticker is favorited, or null if it doesn't exist
   */
  fun isFavorite(packId: String, stickerId: Int): Boolean? {
    return readableDatabase
      .select(Sticker.FAVORITED_AT)
      .from(Sticker.TABLE_NAME)
      .where("${Sticker.PACK_ID} = ? AND ${Sticker.STICKER_ID} = ? AND ${Sticker.COVER} = 0", packId, stickerId)
      .run()
      .readToSingleObject { it.requireLong(Sticker.FAVORITED_AT) > 0 }
  }

  fun hasStickerFile(packId: String, stickerId: Int): Boolean {
    return readableDatabase
      .exists(Sticker.TABLE_NAME)
      .where("${Sticker.PACK_ID} = ? AND ${Sticker.STICKER_ID} = ? AND ${Sticker.COVER} = 0 AND ${Sticker.FILE_PATH} != ''", packId, stickerId)
      .run()
  }

  /**
   * Sets favorite status of a sticker when you know it's there. If it's not, see [insertFavorite]
   */
  fun setFavorite(packId: String, stickerId: Int, isFavorite: Boolean) {
    val now = System.currentTimeMillis()

    val updated = writableDatabase
      .update(Sticker.TABLE_NAME)
      .values(
        Sticker.FAVORITED_AT to if (isFavorite) now else 0,
        Sticker.UNFAVORITED_AT to if (isFavorite) 0 else now,
        Sticker.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey())
      )
      .where("${Sticker.PACK_ID} = ? AND ${Sticker.STICKER_ID} = ? AND ${Sticker.COVER} = 0", packId, stickerId)
      .run()

    if (updated > 0) {
      StorageSyncHelper.scheduleSyncForDataChange()
    }

    notifyStickerListeners()
  }

  /**
   * Inserts a favorited sticker that has not been downloaded (eg from an incoming message). If the stickers already exists, use [setFavorite]
   */
  @Throws(IOException::class)
  fun insertFavorite(packId: String, packKey: String, stickerId: Int, emoji: String, contentType: String?, dataStream: InputStream) {
    val fileInfo: FileInfo = saveStickerImage(dataStream)
    val now = System.currentTimeMillis()

    val existingFilePath: String? = writableDatabase.withinTransaction { db ->
      // Temporarily insert pack stub, download pack below.
      db
        .insertInto(Pack.TABLE_NAME)
        .values(
          Pack.PACK_ID to packId,
          Pack.PACK_KEY to packKey,
          Pack.PACK_TITLE to "",
          Pack.PACK_AUTHOR to "",
          Pack.INSTALLED to 0
        )
        .run(SQLiteDatabase.CONFLICT_IGNORE)

      // Updates pack key in case it was cleared from an unfavorited storage service sticker
      db
        .update(Pack.TABLE_NAME)
        .values(Pack.PACK_KEY to packKey)
        .where("${Pack.PACK_ID} = ? AND ${Pack.PACK_KEY} = ''", packId)
        .run()

      // Writes new file into sticker, removes old one if exists
      val filePath: String? = db
        .select(Sticker.FILE_PATH)
        .from(Sticker.TABLE_NAME)
        .where("${Sticker.PACK_ID} = ? AND ${Sticker.STICKER_ID} = ? AND ${Sticker.COVER} = 0", packId, stickerId)
        .run()
        .readToSingleObject { it.requireNonNullString(Sticker.FILE_PATH) }

      val values = contentValuesOf(
        Sticker.EMOJI to emoji,
        Sticker.CONTENT_TYPE to contentType,
        Sticker.FILE_PATH to fileInfo.file.absolutePath,
        Sticker.FILE_LENGTH to fileInfo.length,
        Sticker.FILE_RANDOM to fileInfo.random,
        Sticker.FAVORITED_AT to now,
        Sticker.UNFAVORITED_AT to 0,
        Sticker.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey())
      )

      if (filePath != null) {
        db
          .update(Sticker.TABLE_NAME)
          .values(values)
          .where("${Sticker.PACK_ID} = ? AND ${Sticker.STICKER_ID} = ? AND ${Sticker.COVER} = 0", packId, stickerId)
          .run()
      } else {
        values.put(Sticker.PACK_ID, packId)
        values.put(Sticker.STICKER_ID, stickerId)
        values.put(Sticker.COVER, 0)

        db
          .insertInto(Sticker.TABLE_NAME)
          .values(values)
          .run()
      }

      filePath
    }

    if (!isPackAvailableAsReference(packId)) {
      AppDependencies.jobManager.add(StickerPackDownloadJob.forReference(packId, packKey))
    }

    if (existingFilePath.isNullOrEmpty()) {
      AppDependencies.jobManager.add(StickerDownloadJob.forFavorite(packId, packKey, stickerId, emoji, contentType))
    } else {
      File(existingFilePath).delete()
    }

    StorageSyncHelper.scheduleSyncForDataChange()
    notifyStickerListeners()
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
          ) AND
          ${Pack.PACK_ID} NOT IN (
            SELECT DISTINCT ${Sticker.PACK_ID}
            FROM ${Sticker.TABLE_NAME}
            WHERE ${Sticker.STORAGE_SERVICE_ID} NOT NULL
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
        deleteStickersInPackExceptCoverAndFavorites(db, packId.value)
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

  /**
   * Attempts to get a favorited or unfavorited sticker for storage service, depending on [packId] and [stickerId]
   */
  fun getFavoriteForStorageSync(packId: StickerPackId, stickerId: Int): FavoriteStickerSyncRecord? {
    return getFavoriteForStorageSync(SqlUtil.buildQuery("${Sticker.TABLE_NAME}.${Sticker.PACK_ID} = ? AND ${Sticker.TABLE_NAME}.${Sticker.STICKER_ID} = ?", packId.value, stickerId))
  }

  /**
   * Attempts to get a favorited or unfavorited sticker for storage service depending on [id]
   */
  fun getFavoriteForStorageSync(id: Long): FavoriteStickerSyncRecord? {
    return getFavoriteForStorageSync(SqlUtil.buildQuery("${Sticker.TABLE_NAME}.${Sticker.ID} = ?", id))
  }

  /**
   * Attempts to get a favorited or unfavorited sticker for storage service, filtered on [query]
   */
  fun getFavoriteForStorageSync(query: SqlUtil.Query): FavoriteStickerSyncRecord? {
    return readableDatabase
      .select(
        "${Sticker.TABLE_NAME}.${Sticker.ID}",
        "${Sticker.TABLE_NAME}.${Sticker.PACK_ID}",
        "${Pack.TABLE_NAME}.${Pack.PACK_KEY}",
        "${Sticker.TABLE_NAME}.${Sticker.STICKER_ID}",
        "${Sticker.TABLE_NAME}.${Sticker.FAVORITED_AT}",
        "${Sticker.TABLE_NAME}.${Sticker.UNFAVORITED_AT}",
        "${Sticker.TABLE_NAME}.${Sticker.STORAGE_SERVICE_ID}",
        "${Sticker.TABLE_NAME}.${Sticker.STORAGE_SERVICE_PROTO}"
      )
      .from(JOINED_TABLES)
      .where("${query.where} AND ${Sticker.TABLE_NAME}.${Sticker.COVER} = 0", query.whereArgs)
      .run()
      .readToSingleObject { cursor ->
        FavoriteStickerSyncRecord(
          rowId = cursor.requireLong(Sticker.ID),
          packId = cursor.requireNonNullString(Sticker.PACK_ID),
          packKey = cursor.requireNonNullString(Pack.PACK_KEY),
          stickerId = cursor.requireInt(Sticker.STICKER_ID),
          favoritedAt = cursor.requireLong(Sticker.FAVORITED_AT),
          unfavoritedAt = cursor.requireLong(Sticker.UNFAVORITED_AT),
          storageServiceId = Base64.decodeOrNull(cursor.requireString(Sticker.STORAGE_SERVICE_ID)),
          storageServiceProto = Base64.decodeOrNull(cursor.requireString(Sticker.STORAGE_SERVICE_PROTO))
        )
      }
  }

  /**
   * Gets all stickers with storage service ids
   */
  fun getFavoriteStorageSyncIds(): List<StorageId> {
    return readableDatabase
      .select(Sticker.STORAGE_SERVICE_ID)
      .from(Sticker.TABLE_NAME)
      .where("${Sticker.STORAGE_SERVICE_ID} NOT NULL")
      .run()
      .readToList { cursor ->
        StorageId.forFavoriteSticker(Base64.decodeOrThrow(cursor.requireNonNullString(Sticker.STORAGE_SERVICE_ID)))
      }
  }

  /**
   * Maps all stickers with storage service ids to their row id
   */
  fun getFavoriteStorageSyncIdsMap(): Map<Long, StorageId> {
    return readableDatabase
      .select(Sticker.ID, Sticker.STORAGE_SERVICE_ID)
      .from(Sticker.TABLE_NAME)
      .where("${Sticker.STORAGE_SERVICE_ID} NOT NULL")
      .run()
      .readToMap { cursor ->
        val key = Base64.decodeOrThrow(cursor.requireNonNullString(Sticker.STORAGE_SERVICE_ID))
        cursor.requireLong(Sticker.ID) to StorageId.forFavoriteSticker(key)
      }
  }

  /**
   * Saves the new storage id for a favorited sticker
   */
  fun applyFavoriteStorageIdUpdate(rowId: Long, storageId: StorageId) {
    applyFavoriteStorageIdUpdates(mapOf(rowId to storageId))
  }

  /**
   * Saves the new storage ids for all the favorited stickers in the map
   */
  fun applyFavoriteStorageIdUpdates(storageIds: Map<Long, StorageId>) {
    writableDatabase.withinTransaction { db ->
      storageIds.forEach { (rowId, storageId) ->
        db.update(Sticker.TABLE_NAME)
          .values(Sticker.STORAGE_SERVICE_ID to Base64.encodeWithPadding(storageId.raw))
          .where("${Sticker.ID} = ?", rowId)
          .run()
      }
    }
  }

  /**
   * Adds a new favorited sticker from storage service, will download if necessary
   */
  fun insertFavoriteFromStorageSync(record: SignalFavoriteStickerRecord) {
    applyFavoriteFromStorageSync(record)
  }

  /**
   * Updates an existing favorited sticker from storage service
   */
  fun updateFavoriteFromStorageSync(record: SignalFavoriteStickerRecord) {
    applyFavoriteFromStorageSync(record)
  }

  private fun applyFavoriteFromStorageSync(record: SignalFavoriteStickerRecord) {
    val packId = Hex.toStringCondensed(record.proto.packId.toByteArray())
    val packKey = Hex.toStringCondensed(record.proto.packKey.toByteArray())
    val stickerId = record.proto.stickerId
    val deleted = record.proto.deletedAtTimestamp > 0
    val storageServiceProto = if (record.proto.hasUnknownFields()) Base64.encodeWithPadding(record.serializedUnknowns!!) else null

    writableDatabase.withinTransaction { db ->
      // Favorites can come from uninstalled packs so attempt to put a placeholder pack if needed
      db
        .insertInto(Pack.TABLE_NAME)
        .values(
          Pack.PACK_ID to packId,
          Pack.PACK_KEY to packKey,
          Pack.PACK_TITLE to "",
          Pack.PACK_AUTHOR to "",
          Pack.INSTALLED to 0
        )
        .run(SQLiteDatabase.CONFLICT_IGNORE)

      if (packKey.isNotEmpty()) {
        db
          .update(Pack.TABLE_NAME)
          .values(Pack.PACK_KEY to packKey)
          .where("${Pack.PACK_ID} = ?", packId)
          .run()
      }

      val existingFilePath: String? = db
        .select(Sticker.FILE_PATH)
        .from(Sticker.TABLE_NAME)
        .where("${Sticker.PACK_ID} = ? AND ${Sticker.STICKER_ID} = ? AND ${Sticker.COVER} = 0", packId, stickerId)
        .run()
        .readToSingleObject { it.requireNonNullString(Sticker.FILE_PATH) }

      val values = contentValuesOf(
        Sticker.FAVORITED_AT to if (deleted) 0 else record.proto.favoritedAtTimestamp,
        Sticker.UNFAVORITED_AT to record.proto.deletedAtTimestamp,
        Sticker.STORAGE_SERVICE_ID to Base64.encodeWithPadding(record.id.raw),
        Sticker.STORAGE_SERVICE_PROTO to storageServiceProto
      )

      if (existingFilePath != null) {
        db
          .update(Sticker.TABLE_NAME)
          .values(values)
          .where("${Sticker.PACK_ID} = ? AND ${Sticker.STICKER_ID} = ? AND ${Sticker.COVER} = 0", packId, stickerId)
          .run()
      } else {
        // Placeholder until the sticker is downloaded, see StickerPackDownloadJob.forFavoriteSticker
        values.put(Sticker.PACK_ID, packId)
        values.put(Sticker.STICKER_ID, stickerId)
        values.put(Sticker.EMOJI, "")
        values.put(Sticker.COVER, 0)
        values.put(Sticker.FILE_PATH, "")

        db
          .insertInto(Sticker.TABLE_NAME)
          .values(values)
          .run()
      }

      val needsDownload = !deleted && (existingFilePath.isNullOrEmpty() || !isPackAvailableAsReference(packId))

      if (needsDownload) {
        Log.i(TAG, "Enqueuing sticker download job for new favorited sticker from storage service.")
        db.runPostSuccessfulTransaction {
          AppDependencies.jobManager.add(StickerPackDownloadJob.forFavoriteSticker(packId, packKey, stickerId))
        }
      }
    }

    notifyStickerPackListeners()
    notifyStickerListeners()
  }

  /**
   * Removes storage ids from stickers that were unfavorited before [unfavoritedBefore]. For uninstalled stickers, we also delete it.
   */
  fun removeStorageIdsFromOldUnfavoritedStickers(unfavoritedBefore: Long): Int {
    return writableDatabase.withinTransaction { db ->
      var deleted = 0

      db
        .select("${Sticker.TABLE_NAME}.${Sticker.ID}", "${Sticker.TABLE_NAME}.${Sticker.FILE_PATH} AS ${Sticker.FILE_PATH}")
        .from(JOINED_TABLES)
        .where("${Sticker.TABLE_NAME}.${Sticker.STORAGE_SERVICE_ID} NOT NULL AND ${Sticker.UNFAVORITED_AT} > 0 AND ${Sticker.UNFAVORITED_AT} < ? AND ${Pack.INSTALLED} = 0", unfavoritedBefore)
        .run()
        .forEach { cursor ->
          deleteSticker(db, cursor.requireLong(Sticker.ID), cursor.requireString(Sticker.FILE_PATH))
          deleted++
        }

      Log.i(TAG, "Deleting $deleted unfavorited stickers from uninstalled packs")

      val cleared = db
        .update(Sticker.TABLE_NAME)
        .values(Sticker.STORAGE_SERVICE_ID to null)
        .where("${Sticker.STORAGE_SERVICE_ID} NOT NULL AND ${Sticker.UNFAVORITED_AT} > 0 AND ${Sticker.UNFAVORITED_AT} < ?", unfavoritedBefore)
        .run()

      deleted + cleared
    }
  }

  /**
   * Removes storageIds of stickers that are only unfavorited locally
   */
  fun removeStorageIdsFromLocalOnlyUnfavoritedStickers(storageIds: Collection<StorageId>): Int {
    var updated = 0

    SqlUtil.buildCollectionQuery(Sticker.STORAGE_SERVICE_ID, storageIds.map { Base64.encodeWithPadding(it.raw) }, "${Sticker.UNFAVORITED_AT} > 0 AND")
      .forEach { query ->
        updated += writableDatabase
          .update(Sticker.TABLE_NAME)
          .values(Sticker.STORAGE_SERVICE_ID to null)
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
        deleteStickersInPackExceptCoverAndFavorites(db, packId)
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
      Pack.PACK_AUTHOR to sticker.packAuthor
    )

    if (existing == null || becomingInstalled) {
      values.put(Pack.INSTALLED, if (sticker.isInstalled) 1 else 0)
    }

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
    deleteStickersInPackExceptFavorites(db, packId)

    db.delete(Pack.TABLE_NAME)
      .where("${Pack.PACK_ID} = ?", packId)
      .run()
  }

  private fun deleteStickersInPackExceptFavorites(database: SQLiteDatabase, packId: String) {
    database.withinTransaction { db ->
      db.select(Sticker.ID, Sticker.FILE_PATH)
        .from(Sticker.TABLE_NAME)
        .where("${Sticker.PACK_ID} = ? AND ${Sticker.FAVORITED_AT} = 0 AND ${Sticker.STORAGE_SERVICE_ID} IS NULL", packId)
        .run()
        .forEach { cursor ->
          val rowId = cursor.requireLong(Sticker.ID)
          val filePath = cursor.requireString(Sticker.FILE_PATH)

          deleteSticker(db, rowId, filePath)
        }
    }
  }

  private fun deleteStickersInPackExceptCoverAndFavorites(database: SQLiteDatabase, packId: String) {
    database.withinTransaction { db ->
      db.select(Sticker.ID, Sticker.FILE_PATH)
        .from(Sticker.TABLE_NAME)
        .where("${Sticker.PACK_ID} = ? AND ${Sticker.COVER} = 0 AND ${Sticker.FAVORITED_AT} = 0 AND ${Sticker.STORAGE_SERVICE_ID} IS NULL", packId)
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

  class StickerRecordReader(private val cursor: Cursor) : Closeable, Iterable<StickerRecord> {

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
        isCover = cursor.requireBoolean(Sticker.COVER),
        isFavorite = cursor.requireLong(Sticker.FAVORITED_AT) > 0
      )
    }

    fun asSequence(): Sequence<StickerRecord> = sequence {
      var record = getNext()
      while (record != null) {
        yield(record)
        record = getNext()
      }
    }

    override fun close() {
      cursor.close()
    }

    override fun iterator(): Iterator<StickerRecord> {
      return ReaderIterator()
    }

    private inner class ReaderIterator : Iterator<StickerRecord> {
      override fun hasNext(): Boolean {
        return cursor.count != 0 && !cursor.isLast
      }

      override fun next(): StickerRecord {
        return getNext() ?: throw NoSuchElementException()
      }
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
      var record = getNext()
      while (record != null) {
        yield(record)
        record = getNext()
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
