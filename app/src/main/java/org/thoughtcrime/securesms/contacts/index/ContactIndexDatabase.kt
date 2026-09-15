/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.index

import android.app.Application
import android.content.ContentValues
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.signal.core.util.SqlUtil
import org.signal.core.util.Util
import org.signal.core.util.getTableRowCount
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireLongOrNull
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.toInt
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.database.SqlCipherDatabaseHook
import org.thoughtcrime.securesms.database.SqlCipherDeletingErrorHandler
import org.thoughtcrime.securesms.database.SqlCipherLibraryLoader
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.concurrent.atomic.AtomicLong

/**
 * A disposable index of the merged contact list: the system address book and the Signal recipients
 * that are not in it, in one table, in one sort order.
 *
 * Three properties are worth understanding before changing anything here.
 *
 * **The key is ephemeral.** It is 32 random bytes held only in this object, never persisted. A file
 * left behind by a previous process therefore cannot be decrypted by design, which is why opening
 * always deletes and recreates, and why there is no schema version to migrate. Deletion is hygiene
 * rather than the privacy boundary.
 *
 * **The row id is the sort position.** Rows are bulk inserted unsorted into a staging table and then
 * copied across with `row_number() OVER (ORDER BY sort_key)`, so `_id` runs 1..n in display order
 * with no gaps. Paging is a row id range scan, which is the cheapest access path SQLite has, and
 * random access by list index is free. This is why the finished table carries no sort key and needs
 * no index to browse.
 *
 * **Every query must be satisfiable in row id order.** This build of SQLCipher is compiled with
 * `SQLITE_TEMP_STORE=2`, so a sort SQLite cannot answer from an index is materialized in memory
 * instead of on disk. An `ORDER BY` other than `_id` silently reintroduces the memory cost this
 * whole design exists to avoid, and it will not fail visibly until it is an out of memory on a
 * device with a very large address book.
 */
class ContactIndexDatabase private constructor(
  private val application: Application,
  private val indexFileName: String,
  databaseSecret: DatabaseSecret
) : SQLiteOpenHelper(
  application,
  indexFileName,
  databaseSecret.asString(),
  null,
  DATABASE_VERSION,
  0,
  SqlCipherDeletingErrorHandler(indexFileName),
  SqlCipherDatabaseHook(),
  true
) {

  companion object {
    private val TAG = Log.tag(ContactIndexDatabase::class.java)

    private const val FILE_PREFIX = "signal-contact-index-"
    private const val FILE_SUFFIX = ".db"
    private const val DATABASE_VERSION = 1

    private const val KEY_SIZE = 32

    const val TABLE_NAME = "contact_index"
    private const val STAGING_TABLE_NAME = "contact_index_staging"

    const val ID = "_id"
    const val TYPE = "type"
    const val SECTION = "section"
    const val DISPLAY_NAME = "display_name"

    /** Every name we know for the row, folded and space delimited. The only column search reads. */
    const val SEARCH_TEXT = "search_text"

    const val RECIPIENT_ID = "recipient_id"
    const val LOOKUP_KEY = "lookup_key"
    const val CONTACT_ID = "contact_id"

    /** Drives the fallback glyph: a company or bare phone number has no initials worth showing. */
    const val HAS_PERSONAL_NAME = "has_personal_name"
    const val HAS_PHOTO = "has_photo"

    /** Ordering columns, present only while staging. */
    private const val SORT_KEY = "sort_key"

    /** Sorts ahead of [SORT_KEY] so that "#" cannot land among the letters and split a section. */
    private const val SORT_RANK = "sort_rank"

    private val INSERT_COLUMNS = arrayOf(SORT_RANK, SORT_KEY, TYPE, SECTION, DISPLAY_NAME, SEARCH_TEXT, RECIPIENT_ID, LOOKUP_KEY, CONTACT_ID, HAS_PERSONAL_NAME, HAS_PHOTO)

    private val COPY_COLUMNS = listOf(TYPE, SECTION, DISPLAY_NAME, SEARCH_TEXT, RECIPIENT_ID, LOOKUP_KEY, CONTACT_ID, HAS_PERSONAL_NAME, HAS_PHOTO)

    private val ROW_COLUMNS = arrayOf(ID, TYPE, SECTION, DISPLAY_NAME, RECIPIENT_ID, LOOKUP_KEY, CONTACT_ID, HAS_PERSONAL_NAME, HAS_PHOTO)

    private val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $TYPE INTEGER NOT NULL,
        $SECTION TEXT NOT NULL,
        $DISPLAY_NAME TEXT NOT NULL,
        $SEARCH_TEXT TEXT NOT NULL,
        $RECIPIENT_ID INTEGER DEFAULT NULL,
        $LOOKUP_KEY TEXT DEFAULT NULL,
        $CONTACT_ID INTEGER DEFAULT NULL,
        $HAS_PERSONAL_NAME INTEGER NOT NULL DEFAULT 1,
        $HAS_PHOTO INTEGER NOT NULL DEFAULT 0
      )
    """

    private val CREATE_STAGING_TABLE = """
      CREATE TABLE $STAGING_TABLE_NAME (
        $SORT_RANK INTEGER NOT NULL,
        $SORT_KEY BLOB NOT NULL,
        $TYPE INTEGER NOT NULL,
        $SECTION TEXT NOT NULL,
        $DISPLAY_NAME TEXT NOT NULL,
        $SEARCH_TEXT TEXT NOT NULL,
        $RECIPIENT_ID INTEGER DEFAULT NULL,
        $LOOKUP_KEY TEXT DEFAULT NULL,
        $CONTACT_ID INTEGER DEFAULT NULL,
        $HAS_PERSONAL_NAME INTEGER NOT NULL DEFAULT 1,
        $HAS_PHOTO INTEGER NOT NULL DEFAULT 0
      )
    """

    /**
     * Identifies this run of the app. An index file that does not carry it was left behind by a
     * process that is gone, so it is safe to delete.
     */
    private val RUN_ID = System.currentTimeMillis().toString()

    private val instanceCounter = AtomicLong()

    /** Opens a fresh index under a name no other instance can hold. */
    fun create(application: Application): ContactIndexDatabase {
      SqlCipherLibraryLoader.load()

      val name = "$FILE_PREFIX$RUN_ID-${instanceCounter.incrementAndGet()}$FILE_SUFFIX"

      return ContactIndexDatabase(application, name, DatabaseSecret(Util.getSecretBytes(KEY_SIZE)))
    }

    private fun deleteDatabaseFile(application: Application, databaseName: String) {
      if (application.deleteDatabase(databaseName)) {
        Log.i(TAG, "Deleted contact index $databaseName.")
      }
    }

    /**
     * Deletes indexes left behind by earlier runs.
     *
     * Only ever touches names from another run, and [closeAndDelete] only ever touches the name
     * from this one, so the two cannot race over a file no matter when app start schedules this.
     */
    fun deleteAbandonedFiles(application: Application) {
      val directory = application.getDatabasePath("$FILE_PREFIX$FILE_SUFFIX").parentFile

      if (directory == null) {
        Log.w(TAG, "No database directory to scan.")
        return
      }

      directory
        .listFiles { file -> file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX) }
        .orEmpty()
        .map { it.name }
        .filterNot { it.startsWith("$FILE_PREFIX$RUN_ID-") }
        .forEach { deleteDatabaseFile(application, it) }
    }
  }

  /** Closes the index and removes it from disk. */
  fun closeAndDelete() {
    close()
    deleteDatabaseFile(application, indexFileName)
  }

  override fun onCreate(db: SQLiteDatabase) {
    Log.i(TAG, "onCreate()")
    db.execSQL(CREATE_TABLE)
  }

  /**
   * Unreachable in practice. The key is per process, so a file from a previous process cannot be
   * opened at all, let alone upgraded. Recreating rather than throwing keeps a wrong assumption here
   * from taking down the app.
   */
  override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    Log.w(TAG, "onUpgrade($oldVersion, $newVersion) on a disposable index. Recreating.")
    db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
    db?.execSQL(CREATE_TABLE)
  }

  private val database: SupportSQLiteDatabase
    get() = writableDatabase

  /** Runs a whole build in one transaction, returning how many rows the finished index holds. */
  fun withinBuild(block: () -> Unit): Int {
    return database.withinTransaction {
      beginBuild()
      block()
      endBuild()
    }
  }

  private fun beginBuild() {
    database.withinTransaction { db ->
      db.execSQL("DROP TABLE IF EXISTS $STAGING_TABLE_NAME")
      db.execSQL("DELETE FROM $TABLE_NAME")
      db.execSQL(CREATE_STAGING_TABLE)
    }
  }

  /**
   * Bulk inserts a batch of rows into staging. Unordered and unindexed on purpose, so that inserting
   * pays for no tree maintenance.
   */
  fun insert(entries: List<ContactIndexEntry>) {
    if (entries.isEmpty()) {
      return
    }

    val values = entries.map { entry ->
      ContentValues(INSERT_COLUMNS.size).apply {
        // Everything that is not a letter sorts after everything that is, matching the design's
        // trailing "#" section.
        put(SORT_RANK, if (entry.section == ContactDisplayName.SECTION_OTHER) 1 else 0)
        put(SORT_KEY, entry.sortKey)
        put(TYPE, entry.type.id)
        put(SECTION, entry.section)
        put(DISPLAY_NAME, entry.displayName)
        put(SEARCH_TEXT, entry.searchText)
        put(RECIPIENT_ID, entry.recipientId?.toLong())
        put(LOOKUP_KEY, entry.lookupKey)
        put(CONTACT_ID, entry.contactId)
        put(HAS_PERSONAL_NAME, entry.hasPersonalName.toInt())
        put(HAS_PHOTO, entry.hasPhoto.toInt())
      }
    }

    database.withinTransaction { db ->
      SqlUtil.buildBulkInsert(STAGING_TABLE_NAME, INSERT_COLUMNS, values).forEach {
        db.execSQL(it.where, it.whereArgs)
      }
    }
  }

  /**
   * Sorts staging into the finished table, assigning row ids in display order, and drops staging.
   *
   * Ordered by section rank first, so sections stay contiguous, then by collation key, then by
   * display name as a tie break so names that collate equally, differing only by case or accent, land
   * in a deterministic order rather than an arbitrary one.
   */
  private fun endBuild(): Int {
    return database.withinTransaction { db ->
      db.execSQL(
        """
        INSERT INTO $TABLE_NAME ($ID, ${COPY_COLUMNS.joinToString(", ")})
        SELECT row_number() OVER (ORDER BY $SORT_RANK, $SORT_KEY, $DISPLAY_NAME), ${COPY_COLUMNS.joinToString(", ")}
        FROM $STAGING_TABLE_NAME
        """
      )
      db.execSQL("DROP TABLE $STAGING_TABLE_NAME")

      count()
    }
  }

  fun count(): Int {
    return database.getTableRowCount(TABLE_NAME)
  }

  /**
   * A window of the whole list, starting at [startPosition]. Because row ids are contiguous, the
   * start position doubles as a list index and as a row id, so this is a range scan rather than an
   * offset walk.
   */
  fun getPage(startPosition: Long, limit: Int): List<ContactIndexRecord> {
    return database
      .query("SELECT ${ROW_COLUMNS.joinToString(", ")} FROM $TABLE_NAME WHERE $ID >= ? ORDER BY $ID LIMIT ?", arrayOf<Any>(startPosition, limit))
      .readToList { cursor -> cursor.toRecord() }
      .filterNotNull()
  }

  /**
   * A window of the rows matching [query], in the same order as the full list.
   *
   * [startPosition] is a keyset cursor, not an offset, so page by passing the last row's position
   * plus one rather than a count of rows already shown.
   */
  fun search(query: String, startPosition: Long, limit: Int): List<ContactIndexRecord> {
    return database
      .query(
        """
        SELECT ${ROW_COLUMNS.joinToString(", ")} FROM $TABLE_NAME
        WHERE $SEARCH_TEXT LIKE ? ESCAPE '${ContactDisplayName.LIKE_ESCAPE}' AND $ID >= ?
        ORDER BY $ID LIMIT ?
        """,
        arrayOf<Any>(ContactDisplayName.searchPatternFor(query), startPosition, limit)
      )
      .readToList { cursor -> cursor.toRecord() }
      .filterNotNull()
  }

  private fun Cursor.toRecord(): ContactIndexRecord? {
    val type = ContactIndexType.fromId(requireInt(TYPE))

    if (type == null) {
      Log.w(TAG, "Unknown contact index type. Skipping the row.")
      return null
    }

    return ContactIndexRecord(
      position = requireLong(ID),
      type = type,
      section = requireNonNullString(SECTION),
      displayName = requireNonNullString(DISPLAY_NAME),
      recipientId = requireLongOrNull(RECIPIENT_ID)?.let { RecipientId.from(it) },
      lookupKey = requireString(LOOKUP_KEY),
      contactId = requireLongOrNull(CONTACT_ID),
      hasPersonalName = requireBoolean(HAS_PERSONAL_NAME),
      hasPhoto = requireBoolean(HAS_PHOTO)
    )
  }
}
