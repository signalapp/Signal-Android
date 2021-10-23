package org.thoughtcrime.securesms.database;

import android.database.Cursor;

import androidx.annotation.NonNull;

import net.zetetic.database.DatabaseErrorHandler;
import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.Util;

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
  @SuppressWarnings("ConstantConditions")
  public void onCorruption(SQLiteDatabase db) {
    StringBuilder output = new StringBuilder();

    output.append("Database '").append(databaseName).append("' corrupted! Going to try to run some diagnostics.").append("\n");

    boolean pragma1Passes = false;
    boolean pragma2Passes = true;

    output.append(" ===== PRAGMA integrity_check =====").append("\n");
    try (Cursor cursor = db.rawQuery("PRAGMA integrity_check", null)) {
      while (cursor.moveToNext()) {
        String row = CursorUtil.readRowAsString(cursor);
        output.append(row).append("\n");
        if (row.toLowerCase().contains("ok")) {
          pragma1Passes = true;
        }
      }
    } catch (Throwable t) {
      output.append("Failed to do integrity_check!").append("\n")
            .append(Util.convertThrowableToString(t));
    }

    output.append("\n").append("===== PRAGMA cipher_integrity_check =====").append("\n");
    try (Cursor cursor = db.rawQuery("PRAGMA cipher_integrity_check", null)) {
      while (cursor.moveToNext()) {
        output.append(CursorUtil.readRowAsString(cursor)).append("\n");
        pragma2Passes = false;
      }
    } catch (Throwable t) {
      output.append("Failed to do cipher_integrity_check!").append("\n")
            .append(Util.convertThrowableToString(t));
    }

    Log.e(TAG, output.toString());

    if (pragma1Passes && pragma2Passes) {
      throw new DatabaseCorruptedError_BothChecksPass();
    } else if (!pragma1Passes && pragma2Passes) {
      throw new DatabaseCorruptedError_NormalCheckFailsCipherCheckPasses();
    } else if (pragma1Passes && !pragma2Passes) {
      throw new DatabaseCorruptedError_NormalCheckPassesCipherCheckFails();
    } else {
      throw new DatabaseCorruptedError_BothChecksFail();
    }
  }


  public static final class DatabaseCorruptedError_BothChecksPass extends Error {
  }
  public static final class DatabaseCorruptedError_BothChecksFail extends Error {
  }
  public static final class DatabaseCorruptedError_NormalCheckFailsCipherCheckPasses extends Error {
  }
  public static final class DatabaseCorruptedError_NormalCheckPassesCipherCheckFails extends Error {
  }
}
