package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.R
import java.util.LinkedList

class DraftTable(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper), ThreadIdDatabaseReference {
  companion object {
    private val TAG = Log.tag(DraftTable::class.java)
    const val TABLE_NAME = "drafts"
    const val ID = "_id"
    const val THREAD_ID = "thread_id"
    const val DRAFT_TYPE = "type"
    const val DRAFT_VALUE = "value"
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY, 
        $THREAD_ID INTEGER, 
        $DRAFT_TYPE TEXT, 
        $DRAFT_VALUE TEXT
      )
      """

    @JvmField
    val CREATE_INDEXS = arrayOf("CREATE INDEX IF NOT EXISTS draft_thread_index ON $TABLE_NAME ($THREAD_ID);")
  }

  fun replaceDrafts(threadId: Long, drafts: List<Draft>) {
    writableDatabase.withinTransaction { db ->
      val deletedRowCount = db
        .delete(TABLE_NAME)
        .where("$THREAD_ID = ?", threadId)
        .run()
      Log.d(TAG, "[replaceDrafts] Deleted $deletedRowCount rows for thread $threadId")

      for (draft in drafts) {
        val values = contentValuesOf(
          THREAD_ID to threadId,
          DRAFT_TYPE to draft.type,
          DRAFT_VALUE to draft.value
        )
        db.insert(TABLE_NAME, null, values)
      }
    }
  }

  fun clearDrafts(threadId: Long) {
    val deletedRowCount = writableDatabase
      .delete(TABLE_NAME)
      .where("$THREAD_ID = ?", threadId)
      .run()
    Log.d(TAG, "[clearDrafts] Deleted $deletedRowCount rows for thread $threadId")
  }

  fun clearDrafts(threadIds: Set<Long>) {
    val query = SqlUtil.buildSingleCollectionQuery(THREAD_ID, threadIds)
    writableDatabase
      .delete(TABLE_NAME)
      .where(query.where, *query.whereArgs)
      .run()
  }

  fun clearAllDrafts() {
    writableDatabase
      .delete(TABLE_NAME)
      .run()
  }

  fun getDrafts(threadId: Long): Drafts {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$THREAD_ID = ?", threadId)
      .run()
      .readToList { cursor ->
        Draft(
          type = cursor.requireNonNullString(DRAFT_TYPE),
          value = cursor.requireNonNullString(DRAFT_VALUE)
        )
      }
      .asDrafts()
  }

  fun getAllVoiceNoteDrafts(): Drafts {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$DRAFT_TYPE = ?", Draft.VOICE_NOTE)
      .run()
      .readToList { cursor ->
        Draft(
          type = cursor.requireNonNullString(DRAFT_TYPE),
          value = cursor.requireNonNullString(DRAFT_VALUE)
        )
      }
      .asDrafts()
  }

  override fun remapThread(fromId: Long, toId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(THREAD_ID to toId)
      .where("$THREAD_ID = ?", fromId)
      .run()
  }

  private fun List<Draft>.asDrafts(): Drafts {
    return Drafts(this)
  }

  class Draft(val type: String, val value: String) {
    fun getSnippet(context: Context): String {
      return when (type) {
        TEXT -> value
        IMAGE -> context.getString(R.string.DraftDatabase_Draft_image_snippet)
        VIDEO -> context.getString(R.string.DraftDatabase_Draft_video_snippet)
        AUDIO -> context.getString(R.string.DraftDatabase_Draft_audio_snippet)
        LOCATION -> context.getString(R.string.DraftDatabase_Draft_location_snippet)
        QUOTE -> context.getString(R.string.DraftDatabase_Draft_quote_snippet)
        VOICE_NOTE -> context.getString(R.string.DraftDatabase_Draft_voice_note)
        else -> ""
      }
    }

    companion object {
      const val TEXT = "text"
      const val IMAGE = "image"
      const val VIDEO = "video"
      const val AUDIO = "audio"
      const val LOCATION = "location"
      const val QUOTE = "quote"
      const val BODY_RANGES = "mention"
      const val VOICE_NOTE = "voice_note"
    }
  }

  class Drafts(list: List<Draft> = emptyList()) : LinkedList<Draft>() {
    init {
      addAll(list)
    }

    fun addIfNotNull(draft: Draft?) {
      if (draft != null) {
        add(draft)
      }
    }

    fun getDraftOfType(type: String): Draft? {
      return firstOrNull { it.type == type }
    }

    fun getSnippet(context: Context): String {
      val textDraft = getDraftOfType(Draft.TEXT)
      return if (textDraft != null) {
        textDraft.getSnippet(context)
      } else if (size > 0) {
        get(0).getSnippet(context)
      } else {
        ""
      }
    }

    fun getUriSnippet(): Uri? {
      val imageDraft = getDraftOfType(Draft.IMAGE)

      return if (imageDraft?.value != null) {
        Uri.parse(imageDraft.value)
      } else {
        null
      }
    }
  }
}
