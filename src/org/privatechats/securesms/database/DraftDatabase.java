package org.privatechats.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;

import org.privatechats.securesms.R;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.privatechats.securesms.crypto.MasterCipher;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DraftDatabase extends Database {

  private static final String TABLE_NAME  = "drafts";
  public  static final String ID          = "_id";
  public  static final String THREAD_ID   = "thread_id";
  public  static final String DRAFT_TYPE  = "type";
  public  static final String DRAFT_VALUE = "value";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
                                            THREAD_ID + " INTEGER, " + DRAFT_TYPE + " TEXT, " + DRAFT_VALUE + " TEXT);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS draft_thread_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
  };

  public DraftDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public void insertDrafts(MasterCipher masterCipher, long threadId, List<Draft> drafts) {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();

    for (Draft draft : drafts) {
      ContentValues values = new ContentValues(3);
      values.put(THREAD_ID, threadId);
      values.put(DRAFT_TYPE, masterCipher.encryptBody(draft.getType()));
      values.put(DRAFT_VALUE, masterCipher.encryptBody(draft.getValue()));

      db.insert(TABLE_NAME, null, values);
    }
  }

  public void clearDrafts(long threadId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, THREAD_ID + " = ?", new String[] {threadId+""});
  }

  public void clearDrafts(Set<Long> threadIds) {
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

  public void clearAllDrafts() {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }

  public List<Draft> getDrafts(MasterCipher masterCipher, long threadId) {
    SQLiteDatabase db   = databaseHelper.getReadableDatabase();
    List<Draft> results = new LinkedList<Draft>();
    Cursor cursor       = null;

    try {
      cursor = db.query(TABLE_NAME, null, THREAD_ID + " = ?", new String[] {threadId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        try {
          String encryptedType  = cursor.getString(cursor.getColumnIndexOrThrow(DRAFT_TYPE));
          String encryptedValue = cursor.getString(cursor.getColumnIndexOrThrow(DRAFT_VALUE));

          results.add(new Draft(masterCipher.decryptBody(encryptedType),
                                masterCipher.decryptBody(encryptedValue)));
        } catch (InvalidMessageException ime) {
          Log.w("DraftDatabase", ime);
        }
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public static class Draft {
    public static final String TEXT     = "text";
    public static final String IMAGE    = "image";
    public static final String VIDEO    = "video";
    public static final String AUDIO    = "audio";
    public static final String LOCATION = "location";

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

    public String getSnippet(Context context) {
      switch (type) {
      case TEXT:     return value;
      case IMAGE:    return context.getString(R.string.DraftDatabase_Draft_image_snippet);
      case VIDEO:    return context.getString(R.string.DraftDatabase_Draft_video_snippet);
      case AUDIO:    return context.getString(R.string.DraftDatabase_Draft_audio_snippet);
      case LOCATION: return context.getString(R.string.DraftDatabase_Draft_location_snippet);
      default:       return null;
      }
    }
  }

  public static class Drafts extends LinkedList<Draft> {
    private Draft getDraftOfType(String type) {
      for (Draft draft : this) {
        if (type.equals(draft.getType())) {
          return draft;
        }
      }
      return null;
    }

    public String getSnippet(Context context) {
      Draft textDraft = getDraftOfType(Draft.TEXT);
      if (textDraft != null) {
        return textDraft.getSnippet(context);
      } else if (size() > 0) {
        return get(0).getSnippet(context);
      } else {
        return "";
      }
    }

    public @Nullable Uri getUriSnippet(Context context) {
      Draft imageDraft = getDraftOfType(Draft.IMAGE);

      if (imageDraft != null && imageDraft.getValue() != null) {
        return Uri.parse(imageDraft.getValue());
      }

      return null;
    }
  }
}
