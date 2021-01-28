package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DraftDatabase extends Database {

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

  public DraftDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public void insertDrafts(long threadId, List<Draft> drafts) {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();

    for (Draft draft : drafts) {
      ContentValues values = new ContentValues(3);
      values.put(THREAD_ID, threadId);
      values.put(DRAFT_TYPE, draft.getType());
      values.put(DRAFT_VALUE, draft.getValue());

      db.insert(TABLE_NAME, null, values);
    }
  }

  public void clearDrafts(long threadId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, THREAD_ID + " = ?", new String[] {threadId+""});
  }

  void clearDrafts(Set<Long> threadIds) {
    SQLiteDatabase db        = databaseHelper.getWritableDatabase();
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
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }

  public Drafts getDrafts(long threadId) {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
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

  public static class Draft {
    public static final String TEXT     = "text";
    public static final String IMAGE    = "image";
    public static final String VIDEO    = "video";
    public static final String AUDIO    = "audio";
    public static final String LOCATION = "location";
    public static final String QUOTE    = "quote";
    public static final String MENTION  = "mention";

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
      case TEXT:     return value;
      case IMAGE:    return context.getString(R.string.DraftDatabase_Draft_image_snippet);
      case VIDEO:    return context.getString(R.string.DraftDatabase_Draft_video_snippet);
      case AUDIO:    return context.getString(R.string.DraftDatabase_Draft_audio_snippet);
      case LOCATION: return context.getString(R.string.DraftDatabase_Draft_location_snippet);
      case QUOTE:    return context.getString(R.string.DraftDatabase_Draft_quote_snippet);
      default:       return null;
      }
    }
  }

  public static class Drafts extends LinkedList<Draft> {
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
