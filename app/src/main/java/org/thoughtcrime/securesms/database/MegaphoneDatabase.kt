package org.thoughtcrime.securesms.database

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.signal.core.util.delete
import org.signal.core.util.forEach
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.SqlCipherLibraryLoader.load
import org.thoughtcrime.securesms.database.model.MegaphoneRecord
import org.thoughtcrime.securesms.megaphone.Megaphones
import kotlin.concurrent.Volatile

/**
 * IMPORTANT: Writes should only be made through [org.thoughtcrime.securesms.megaphone.MegaphoneRepository].
 */
open class MegaphoneDatabase(
  application: Application,
  databaseSecret: DatabaseSecret
) : SQLiteOpenHelper(
  application,
  DATABASE_NAME,
  databaseSecret.asString(),
  null,
  DATABASE_VERSION,
  0,
  SqlCipherErrorHandler(application, DATABASE_NAME),
  SqlCipherDatabaseHook(),
  true
),
  SignalDatabaseOpenHelper {

  companion object {
    private val TAG = Log.tag(MegaphoneDatabase::class.java)

    private const val DATABASE_VERSION = 2
    private const val DATABASE_NAME = "signal-megaphone.db"

    private const val TABLE_NAME = "megaphone"
    private const val ID = "_id"

    /**
     * The event name, which is a key we use to tie it to views and whatnot.
     */
    private const val EVENT = "event"

    /**
     * How many times a megaphone was interacted with. This is most commonly the "snooze" count.
     */
    private const val INTERACTION_COUNT = "interaction_count"

    /**
     * The last time a megaphone was interacted with. This is most commonly the "snooze" timestamp.
     */
    private const val LAST_INTERACTION_TIMESTAMP = "last_interaction_timestamp"

    /**
     * The timestamp of when the megaphone was first shown to the user.
     */
    private const val FIRST_VISIBLE = "first_visible"

    /**
     * The timestamp of then when the last "view cycle" started. For instance, if a megaphone was
     * snoozed and then shown again, this will be the timestamp of when it was first shown again.
     * It is *not* updated every time a megaphone is seen, just at the start of the view cycle.
     * This is largely used to determine when to auto-snooze a megaphone.
     */
    private const val LAST_VISIBLE = "last_visible"

    /**
     * Whether a megaphone has been fully completed. When it's finished, it'll never be shown again.
     */
    private const val FINISHED = "finished"

    const val CREATE_TABLE: String = """CREATE TABLE $TABLE_NAME(
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $EVENT TEXT UNIQUE,
        $INTERACTION_COUNT INTEGER,
        $LAST_INTERACTION_TIMESTAMP INTEGER,
        $FIRST_VISIBLE INTEGER,
        $LAST_VISIBLE INTEGER DEFAULT 0,
        $FINISHED INTEGER
    )"""

    @Volatile
    private var instance: MegaphoneDatabase? = null

    @JvmStatic
    fun getInstance(context: Application): MegaphoneDatabase {
      if (instance == null) {
        synchronized(MegaphoneDatabase::class.java) {
          if (instance == null) {
            load()
            instance = MegaphoneDatabase(context, DatabaseSecretProvider.getOrCreateDatabaseSecret(context))
          }
        }
      }
      return instance!!
    }
  }

  @get:VisibleForTesting
  internal open val database: SupportSQLiteDatabase
    get() = writableDatabase

  override fun onCreate(db: SQLiteDatabase) {
    Log.i(TAG, "onCreate()")
    db.execSQL(CREATE_TABLE)
  }

  override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    Log.i(TAG, "onUpgrade($oldVersion, $newVersion)")

    if (oldVersion < 2) {
      db!!.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $LAST_VISIBLE INTEGER DEFAULT 0")
      db.execSQL("ALTER TABLE $TABLE_NAME RENAME COLUMN seen_count TO interaction_count")
      db.execSQL("ALTER TABLE $TABLE_NAME RENAME COLUMN last_seen TO last_interaction_timestamp")
    }
  }

  override fun onOpen(db: SQLiteDatabase) {
    Log.i(TAG, "onOpen()")
    db.setForeignKeyConstraintsEnabled(true)
  }

  fun insert(events: Collection<Megaphones.Event>) {
    database.withinTransaction { db ->
      for (event in events) {
        db.insertInto(TABLE_NAME)
          .values(EVENT to event.key)
          .run(SQLiteDatabase.CONFLICT_IGNORE)
      }
    }
  }

  fun getAllAndDeleteMissing(): MutableList<MegaphoneRecord> {
    val records: MutableList<MegaphoneRecord> = mutableListOf()

    database.withinTransaction { db ->
      val missingKeys: MutableSet<String> = mutableSetOf()

      db.select()
        .from(TABLE_NAME)
        .run()
        .forEach { cursor ->
          val event = cursor.requireNonNullString(EVENT)
          val interactionCount = cursor.requireInt(INTERACTION_COUNT)
          val lastInteractionTime = cursor.requireLong(LAST_INTERACTION_TIMESTAMP)
          val firstVisible = cursor.requireLong(FIRST_VISIBLE)
          val lastVisible = cursor.requireLong(LAST_VISIBLE)
          val finished = cursor.requireBoolean(FINISHED)

          if (Megaphones.Event.hasKey(event)) {
            records += MegaphoneRecord(
              event = Megaphones.Event.fromKey(event),
              interactionCount = interactionCount,
              lastInteractionTime = lastInteractionTime,
              firstVisible = firstVisible,
              lastVisible = lastVisible,
              finished = finished
            )
          } else {
            Log.w(TAG, "No in-app handing for event '$event'! Deleting it from the database.")
            missingKeys += event
          }
        }

      for (missing in missingKeys) {
        db.delete(TABLE_NAME)
          .where("$EVENT = ?", missing)
          .run()
      }
    }

    return records
  }

  fun markFirstVisible(event: Megaphones.Event, time: Long) {
    database
      .update(TABLE_NAME)
      .values(FIRST_VISIBLE to time)
      .where("$EVENT = ?", event.key)
      .run()
  }

  fun markLastVisible(event: Megaphones.Event, time: Long) {
    database
      .update(TABLE_NAME)
      .values(LAST_VISIBLE to time)
      .where("$EVENT = ?", event.key)
      .run()
  }

  fun markInteractedWith(event: Megaphones.Event, interactionCount: Int, lastInteractionTimestamp: Long) {
    database
      .update(TABLE_NAME)
      .values(
        INTERACTION_COUNT to interactionCount,
        LAST_INTERACTION_TIMESTAMP to lastInteractionTimestamp,
        LAST_VISIBLE to 0L
      )
      .where("$EVENT = ?", event.key)
      .run()
  }

  fun markFinished(event: Megaphones.Event) {
    database
      .update(TABLE_NAME)
      .values(FINISHED to 1)
      .where("$EVENT = ?", event.key)
      .run()
  }

  fun delete(event: Megaphones.Event) {
    database
      .delete(TABLE_NAME)
      .where("$EVENT = ?", event.key)
      .run()
  }

  override fun getSqlCipherDatabase(): SQLiteDatabase {
    return writableDatabase
  }
}
