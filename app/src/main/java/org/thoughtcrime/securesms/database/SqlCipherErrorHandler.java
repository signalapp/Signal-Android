package org.thoughtcrime.securesms.database;

import android.database.Cursor;

import androidx.annotation.NonNull;

import net.zetetic.database.DatabaseErrorHandler;
import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.CursorUtil;

/**
 * The default error handler wipes the file. This one instead prints some diagnostics and then crashes so the original corrupt file isn't lost.
 */
public final class SqlCipherErrorHandler implements DatabaseErrorHandler {

  private static final String TAG = Log.tag(SqlCipherErrorHandler.class);

  private final String databaseName;

  public SqlCipherErrorHandler(@NonNull String databaseName) {
    this.databaseName = databaseName;
  }

  @Override
  public void onCorruption(SQLiteDatabase db) {
    Log.e(TAG, "Database '" + databaseName + "' corrupted! Going to try to run some diagnostics.");

    Log.w(TAG, " ===== PRAGMA integrity_check =====");
    try (Cursor cursor = db.rawQuery("PRAGMA integrity_check", null)) {
      while (cursor.moveToNext()) {
        Log.w(TAG, CursorUtil.readRowAsString(cursor));
      }
    } catch (Throwable t) {
      Log.e(TAG, "Failed to do integrity_check!", t);
    }

    Log.w(TAG, "===== PRAGMA cipher_integrity_check =====");
    try (Cursor cursor = db.rawQuery("PRAGMA cipher_integrity_check", null)) {
      while (cursor.moveToNext()) {
        Log.w(TAG, CursorUtil.readRowAsString(cursor));
      }
    } catch (Throwable t) {
      Log.e(TAG, "Failed to do cipher_integrity_check!", t);
    }

    throw new DatabaseCorruptedError();
  }

  public static final class DatabaseCorruptedError extends Error {
  }
}
