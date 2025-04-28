package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.StreamUtil
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.forEach
import org.signal.core.util.insertInto
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.signal.core.util.readToSet
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
import org.thoughtcrime.securesms.crypto.AttachmentSecret
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream
import org.thoughtcrime.securesms.database.model.IncomingSticker
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.database.model.StickerRecord
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri
import org.thoughtcrime.securesms.stickers.BlessedPacks
import org.thoughtcrime.securesms.stickers.StickerPackInstallEvent
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream

class StickerTable(
  context: Context?,
  databaseHelper: SignalDatabase?,
  private val attachmentSecret: AttachmentSecret
) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val TAG = tag(StickerTable::class.java)

    const val TABLE_NAME: String = "sticker"
    const val ID: String = "_id"
    const val PACK_ID: String = "pack_id"
    const val PACK_KEY: String = "pack_key"
    const val PACK_TITLE: String = "pack_title"
    const val PACK_AUTHOR: String = "pack_author"
    private const val STICKER_ID = "sticker_id"
    const val EMOJI: String = "emoji"
    const val CONTENT_TYPE: String = "content_type"
    const val COVER: String = "cover"
    private const val PACK_ORDER = "pack_order"
    const val INSTALLED: String = "installed"
    private const val LAST_USED = "last_used"
    const val FILE_PATH: String = "file_path"
    const val FILE_LENGTH: String = "file_length"
    const val FILE_RANDOM: String = "file_random"

    val CREATE_TABLE: String = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $PACK_ID TEXT NOT NULL,
        $PACK_KEY TEXT NOT NULL,
        $PACK_TITLE TEXT NOT NULL,
        $PACK_AUTHOR TEXT NOT NULL,
        $STICKER_ID INTEGER,
        $COVER INTEGER,
        $PACK_ORDER INTEGER,
        $EMOJI TEXT NOT NULL,
        $CONTENT_TYPE TEXT DEFAULT NULL,
        $LAST_USED INTEGER,
        $INSTALLED INTEGER,
        $FILE_PATH TEXT NOT NULL,
        $FILE_LENGTH INTEGER,
        $FILE_RANDOM BLOB,
        UNIQUE($PACK_ID, $STICKER_ID, $COVER) ON CONFLICT IGNORE
      )
      """

    val CREATE_INDEXES: Array<String> = arrayOf(
      "CREATE INDEX IF NOT EXISTS sticker_pack_id_index ON $TABLE_NAME ($PACK_ID);",
      "CREATE INDEX IF NOT EXISTS sticker_sticker_id_index ON $TABLE_NAME ($STICKER_ID);"
    )

    const val DIRECTORY: String = "stickers"
  }

  @Throws(IOException::class)
  fun insertSticker(sticker: IncomingSticker, dataStream: InputStream, notify: Boolean) {
    val fileInfo: FileInfo = saveStickerImage(dataStream)

    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        PACK_ID to sticker.packId,
        PACK_KEY to sticker.packKey,
        PACK_TITLE to sticker.packTitle,
        PACK_AUTHOR to sticker.packAuthor,
        STICKER_ID to sticker.stickerId,
        EMOJI to sticker.emoji,
        CONTENT_TYPE to sticker.contentType,
        COVER to if (sticker.isCover) 1 else 0,
        INSTALLED to if (sticker.isInstalled) 1 else 0,
        FILE_PATH to fileInfo.file.absolutePath,
        FILE_LENGTH to fileInfo.length,
        FILE_RANDOM to fileInfo.random
      )
      .run(SQLiteDatabase.CONFLICT_REPLACE)

    notifyStickerListeners()

    if (sticker.isCover) {
      notifyStickerPackListeners()

      if (sticker.isInstalled && notify) {
        broadcastInstallEvent(sticker.packId)
      }
    }
  }

  fun getSticker(packId: String, stickerId: Int, isCover: Boolean): StickerRecord? {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$PACK_ID = ? AND $STICKER_ID = ? AND $COVER = ?", packId, stickerId.toString(), isCover.toInt())
      .run()
      .readToSingleObject { it.readStickerRecord() }
  }

  fun getStickerPack(packId: String): StickerPackRecord? {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$PACK_ID = ? AND $COVER = 1", packId)
      .run()
      .readToSingleObject { it.readStickerPackRecord() }
  }

  fun getInstalledStickerPacks(): Cursor {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$COVER = 1 AND $INSTALLED = 1")
      .orderBy("$PACK_ORDER ASC")
      .run()
  }

  fun getStickersByEmoji(emoji: String): Cursor {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$EMOJI LIKE ? AND $COVER = 0", "%$emoji%")
      .run()
  }

  fun getAllStickerPacks(): Cursor {
    return getAllStickerPacks(null)
  }

  fun getAllStickerPacks(limit: String?): Cursor {
    return readableDatabase.query(
      TABLE_NAME,
      null,
      "$COVER = 1",
      null,
      PACK_ID,
      null,
      "$PACK_ORDER ASC",
      limit
    )
  }

  fun getStickersForPack(packId: String): Cursor {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$PACK_ID = ? AND $COVER = 0", packId)
      .orderBy("$STICKER_ID ASC")
      .run()
  }

  fun getRecentlyUsedStickers(limit: Int): Cursor {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$LAST_USED > 0 AND $COVER = 0")
      .orderBy("$LAST_USED DESC")
      .limit(limit)
      .run()
  }

  fun getAllStickerFiles(): Set<String> {
    return readableDatabase
      .select(FILE_PATH)
      .from(TABLE_NAME)
      .run()
      .readToSet { it.requireNonNullString(FILE_PATH) }
  }

  @Throws(IOException::class)
  fun getStickerStream(rowId: Long): InputStream? {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$ID = ?", rowId)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          val path = cursor.requireString(FILE_PATH)
          val random = cursor.requireBlob(FILE_RANDOM)

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
      .exists(TABLE_NAME)
      .where("$PACK_ID = ? AND $COVER = 1", packId)
      .run()
  }

  fun updateStickerLastUsedTime(rowId: Long, lastUsed: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(LAST_USED to lastUsed)
      .where("$ID = ?", rowId)
      .run()

    notifyStickerListeners()
    notifyStickerPackListeners()
  }

  fun markPackAsInstalled(packId: String, notify: Boolean) {
    updatePackInstalled(
      db = databaseHelper.signalWritableDatabase,
      packId = packId,
      installed = true,
      notify = notify
    )
    notifyStickerPackListeners()
  }

  fun deleteOrphanedPacks() {
    var performedDelete = false

    writableDatabase.withinTransaction { db ->
      db.rawQuery(
        """
        SELECT $PACK_ID 
        FROM $TABLE_NAME 
        WHERE 
          $INSTALLED = 0 AND 
          $PACK_ID NOT IN (
            SELECT DISTINCT ${AttachmentTable.STICKER_PACK_ID}
            FROM ${AttachmentTable.TABLE_NAME}
            WHERE ${AttachmentTable.STICKER_PACK_ID} NOT NULL
          )
        """,
        null
      ).forEach { cursor ->
        val packId = cursor.getString(cursor.getColumnIndexOrThrow(PACK_ID))

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
    writableDatabase.withinTransaction { db ->
      updatePackInstalled(db = db, packId = packId, installed = false, notify = false)
      deleteStickersInPackExceptCover(db, packId)
    }

    notifyStickerPackListeners()
    notifyStickerListeners()
  }

  fun updatePackOrder(packsInOrder: List<StickerPackRecord>) {
    writableDatabase.withinTransaction { db ->
      for ((i, pack) in packsInOrder.withIndex()) {
        db.update(TABLE_NAME)
          .values(PACK_ORDER to i)
          .where("$PACK_ID = ? AND $COVER = 1", pack.packId)
          .run()
      }
    }

    notifyStickerPackListeners()
  }

  private fun updatePackInstalled(db: SQLiteDatabase, packId: String, installed: Boolean, notify: Boolean) {
    val existing = getStickerPack(packId)

    if (existing != null && existing.isInstalled == installed) {
      return
    }

    db.update(TABLE_NAME)
      .values(INSTALLED to installed.toInt())
      .where("$PACK_ID = ?", packId)
      .run()

    if (installed && notify) {
      broadcastInstallEvent(packId)
    }
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
    db.delete(TABLE_NAME)
      .where("$ID = ?", rowId)
      .run()

    if (filePath.isNotNullOrBlank()) {
      File(filePath).delete()
    }
  }

  private fun deletePack(db: SQLiteDatabase, packId: String) {
    db.delete(TABLE_NAME)
      .where("$PACK_ID = ?", packId)
      .run()

    deleteStickersInPack(db, packId)
  }

  private fun deleteStickersInPack(database: SQLiteDatabase, packId: String) {
    database.withinTransaction { db ->
      db.select(ID, FILE_PATH)
        .from(TABLE_NAME)
        .where("$PACK_ID = ?", packId)
        .run()
        .forEach { cursor ->
          val rowId = cursor.requireLong(ID)
          val filePath = cursor.requireString(FILE_PATH)

          deleteSticker(db, rowId, filePath)
        }

      db.delete(TABLE_NAME)
        .where("$PACK_ID = ?", packId)
        .run()
    }
  }

  private fun deleteStickersInPackExceptCover(database: SQLiteDatabase, packId: String) {
    database.withinTransaction { db ->
      db.select(ID, FILE_PATH)
        .from(TABLE_NAME)
        .where("$PACK_ID = ? AND $COVER = 0", packId)
        .run()
        .forEach { cursor ->
          val rowId = cursor.requireLong(ID)
          val filePath = cursor.requireString(FILE_PATH)

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
        rowId = cursor.requireLong(ID),
        packId = cursor.requireNonNullString(PACK_ID),
        packKey = cursor.requireNonNullString(PACK_KEY),
        stickerId = cursor.requireInt(STICKER_ID),
        emoji = cursor.requireNonNullString(EMOJI),
        contentType = cursor.requireString(CONTENT_TYPE) ?: MediaUtil.IMAGE_WEBP,
        size = cursor.requireLong(FILE_LENGTH),
        isCover = cursor.requireBoolean(COVER)
      )
    }

    override fun close() {
      cursor.close()
    }
  }

  class StickerPackRecordReader(private val cursor: Cursor) : Closeable {

    fun getNext(): StickerPackRecord? {
      if (!cursor.moveToNext()) {
        return null
      }

      return getCurrent()
    }

    fun getCurrent(): StickerPackRecord {
      val cover = StickerRecordReader(cursor).getCurrent()

      return StickerPackRecord(
        packId = cursor.requireNonNullString(PACK_ID),
        packKey = cursor.requireNonNullString(PACK_KEY),
        title = cursor.requireNonNullString(PACK_TITLE),
        author = cursor.requireNonNullString(PACK_AUTHOR),
        cover = cover,
        isInstalled = cursor.requireBoolean(INSTALLED)
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
  }
}
