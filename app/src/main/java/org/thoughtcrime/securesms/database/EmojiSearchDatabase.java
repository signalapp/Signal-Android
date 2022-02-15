package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.EmojiSearchData;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.FtsUtil;
import org.thoughtcrime.securesms.util.SqlUtil;

import java.util.LinkedList;
import java.util.List;

/**
 * Contains all info necessary for full-text search of emoji tags.
 */
public class EmojiSearchDatabase extends Database {

  public static final String TABLE_NAME = "emoji_search";

  public static final String LABEL = "label";
  public static final String EMOJI = "emoji";

  public static final String CREATE_TABLE = "CREATE VIRTUAL TABLE " + TABLE_NAME + " USING fts5(" + LABEL + ", " + EMOJI + " UNINDEXED)";

  public EmojiSearchDatabase(@NonNull Context context, @NonNull SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  /**
   * @param query A search query. Doesn't need any special formatted -- it'll be sanitized.
   * @return A list of emoji that are related to the search term, ordered by relevance.
   */
  public @NonNull List<String> query(@NonNull String query, int limit) {
    SQLiteDatabase db          = databaseHelper.getSignalReadableDatabase();
    String         matchString = FtsUtil.createPrefixMatchString(query);
    List<String>   results     = new LinkedList<>();

    if (TextUtils.isEmpty(matchString)) {
      return results;
    }

    String[] projection = new String[] { EMOJI };
    String   selection  = LABEL + " MATCH (?)";
    String[] args       = SqlUtil.buildArgs(matchString);

    try (Cursor cursor = db.query(true, TABLE_NAME, projection, selection, args, null, null,"rank", String.valueOf(limit))) {
      while (cursor.moveToNext()) {
        results.add(CursorUtil.requireString(cursor, EMOJI));
      }
    }

    return results;
  }

  /**
   * Deletes the content of the current search index and replaces it with the new one.
   */
  public void setSearchIndex(@NonNull List<EmojiSearchData> searchIndex) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    db.beginTransaction();
    try {
      db.delete(TABLE_NAME, null, null);

      for (EmojiSearchData searchData : searchIndex) {
        for (String label : searchData.getTags()) {
          ContentValues values = new ContentValues(2);
          values.put(LABEL, label);
          values.put(EMOJI, searchData.getEmoji());
          db.insert(TABLE_NAME, null, values);
        }
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }
}

