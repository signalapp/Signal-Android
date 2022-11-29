package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.signal.core.util.CursorUtil;
import org.signal.core.util.SqlUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DraftTable extends DatabaseTable implements ThreadIdDatabaseReference {

  private static final String TAG = Log.tag(DraftTable.class);

          static final String TABLE_NAME  = "drafts";
  public  static final String ID          = "_id";
  public  static final String THREAD_ID   = "thread_id";
  public  static final String DRAFT_TYPE  = "type";
  public  static final String DRAFT_VALUE = "value";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
                                            THREAD_ID + " INTEGER, " + DRAFT_TYPE + " TEXT, " + DRAFT_VALUE + " TEXT);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS draft_thread_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
  };

  public DraftTable(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  public void replaceDrafts(long threadId, List<Draft> drafts) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    try {
      db.beginTransaction();

      int deletedRowCount = db.delete(TABLE_NAME, THREAD_ID + " = ?", SqlUtil.buildArgs(threadId));
      Log.d(TAG, "[replaceDrafts] Deleted " + deletedRowCount + " rows for thread " + threadId);

      for (Draft draft : drafts) {
        ContentValues values = new ContentValues(3);
        values.put(THREAD_ID, threadId);
        values.put(DRAFT_TYPE, draft.getType());
        values.put(DRAFT_VALUE, draft.getValue());

        db.insert(TABLE_NAME, null, values);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void clearDrafts(long threadId) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    int deletedRowCount = db.delete(TABLE_NAME, THREAD_ID + " = ?", SqlUtil.buildArgs(threadId));
    Log.d(TAG, "[clearDrafts] Deleted " + deletedRowCount + " rows for thread " + threadId);
  }

  void clearDrafts(Set<Long> threadIds) {
    SQLiteDatabase db        = databaseHelper.getSignalWritableDatabase();
    StringBuilder  where     = new StringBuilder();
    List<String>   arguments = new LinkedList<>();

    for (long threadId : threadIds) {
      where.append(" OR ")
           .append(THREAD_ID)
           .append(" = ?");

      arguments.add(String.valueOf(threadId));
    }

    db.delete(TABLE_NAME, where.toString().substring(4), arguments.toArray(new String[0]));
  }

  void clearAllDrafts() {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }

  public Drafts getDrafts(long threadId) {
    SQLiteDatabase db      = databaseHelper.getSignalReadableDatabase();
    Drafts         results = new Drafts();

    try (Cursor cursor = db.query(TABLE_NAME, null, THREAD_ID + " = ?", new String[] {threadId+""}, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        String type  = cursor.getString(cursor.getColumnIndexOrThrow(DRAFT_TYPE));
        String value = cursor.getString(cursor.getColumnIndexOrThrow(DRAFT_VALUE));

        results.add(new Draft(type, value));
      }

      return results;
    }
  }

  public @NonNull Drafts getAllVoiceNoteDrafts() {
    SQLiteDatabase db      = databaseHelper.getSignalReadableDatabase();
    Drafts         results = new Drafts();
    String         where   = DRAFT_TYPE + " = ?";
    String[]       args    = SqlUtil.buildArgs(Draft.VOICE_NOTE);

    try (Cursor cursor = db.query(TABLE_NAME, null, where, args, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        String type  = CursorUtil.requireString(cursor, DRAFT_TYPE);
        String value = CursorUtil.requireString(cursor, DRAFT_VALUE);

        results.add(new Draft(type, value));
      }

      return results;
    }
  }

  @Override
  public void remapThread(long fromId, long toId) {
    ContentValues values = new ContentValues();
    values.put(THREAD_ID, toId);
    getWritableDatabase().update(TABLE_NAME, values, THREAD_ID + " = ?", SqlUtil.buildArgs(fromId));
  }

  public static class Draft {
    public static final String TEXT       = "text";
    public static final String IMAGE      = "image";
    public static final String VIDEO      = "video";
    public static final String AUDIO      = "audio";
    public static final String LOCATION   = "location";
    public static final String QUOTE      = "quote";
    public static final String MENTION    = "mention";
    public static final String VOICE_NOTE = "voice_note";

    private final String type;
    private final String value;

    public Draft(String type, String value) {
      this.type  = type;
      this.value = value;
    }

    public String getType() {
      return type;
    }

    public String getValue() {
      return value;
    }

    String getSnippet(Context context) {
      switch (type) {
      case TEXT:       return value;
      case IMAGE:      return context.getString(R.string.DraftDatabase_Draft_image_snippet);
      case VIDEO:      return context.getString(R.string.DraftDatabase_Draft_video_snippet);
      case AUDIO:      return context.getString(R.string.DraftDatabase_Draft_audio_snippet);
      case LOCATION:   return context.getString(R.string.DraftDatabase_Draft_location_snippet);
      case QUOTE:      return context.getString(R.string.DraftDatabase_Draft_quote_snippet);
      case VOICE_NOTE: return context.getString(R.string.DraftDatabase_Draft_voice_note);
      default:         return null;
      }
    }
  }

  public static class Drafts extends LinkedList<Draft> {
    public void addIfNotNull(@Nullable Draft draft) {
      if (draft != null) {
        add(draft);
      }
    }

    public @Nullable Draft getDraftOfType(String type) {
      for (Draft draft : this) {
        if (type.equals(draft.getType())) {
          return draft;
        }
      }
      return null;
    }

    public @NonNull String getSnippet(Context context) {
      Draft textDraft = getDraftOfType(Draft.TEXT);
      if (textDraft != null) {
        return textDraft.getSnippet(context);
      } else if (size() > 0) {
        return get(0).getSnippet(context);
      } else {
        return "";
      }
    }

    public @Nullable Uri getUriSnippet() {
      Draft imageDraft = getDraftOfType(Draft.IMAGE);

      if (imageDraft != null && imageDraft.getValue() != null) {
        return Uri.parse(imageDraft.getValue());
      }

      return null;
    }
  }
}
