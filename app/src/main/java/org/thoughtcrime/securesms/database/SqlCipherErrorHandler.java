package org.thoughtcrime.securesms.database;

import android.database.Cursor;

import androidx.annotation.NonNull;

import net.zetetic.database.DatabaseErrorHandler;
import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.signal.core.util.ExceptionUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.CursorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
      pragma1Passes = false;
      output.append("Failed to do integrity_check!").append("\n")
            .append(ExceptionUtil.convertThrowableToString(t));
    }

    output.append("\n").append("===== PRAGMA cipher_integrity_check =====").append("\n");
    try (Cursor cursor = db.rawQuery("PRAGMA cipher_integrity_check", null)) {
      while (cursor.moveToNext()) {
        output.append(CursorUtil.readRowAsString(cursor)).append("\n");
        pragma2Passes = false;
      }
    } catch (Throwable t) {
      pragma2Passes = false;
      output.append("Failed to do cipher_integrity_check!").append("\n")
            .append(ExceptionUtil.convertThrowableToString(t));
    }

    Log.e(TAG, output.toString());

    List<String> lines = new ArrayList<>(Arrays.asList(output.toString().split("\n")));

    if (pragma1Passes && pragma2Passes) {
      throw new DatabaseCorruptedError_BothChecksPass(lines);
    } else if (!pragma1Passes && pragma2Passes) {
      throw new DatabaseCorruptedError_NormalCheckFailsCipherCheckPasses(lines);
    } else if (pragma1Passes && !pragma2Passes) {
      throw new DatabaseCorruptedError_NormalCheckPassesCipherCheckFails(lines);
    } else {
      throw new DatabaseCorruptedError_BothChecksFail(lines);
    }
  }

  public static class CustomTraceError extends Error {

    CustomTraceError(@NonNull List<String> lines) {
      StackTraceElement[] custom = lines.stream().map(line -> new StackTraceElement(line, "", "", 0)).toArray(StackTraceElement[]::new);

      setStackTrace(ExceptionUtil.joinStackTrace(getStackTrace(), custom));
    }
  }

  public static final class DatabaseCorruptedError_BothChecksPass extends CustomTraceError {
    DatabaseCorruptedError_BothChecksPass(@NonNull List<String> lines) {
      super(lines);
    }
  }

  public static final class DatabaseCorruptedError_BothChecksFail extends CustomTraceError {
    DatabaseCorruptedError_BothChecksFail(@NonNull List<String> lines) {
      super(lines);
    }
  }

  public static final class DatabaseCorruptedError_NormalCheckFailsCipherCheckPasses extends CustomTraceError {
    DatabaseCorruptedError_NormalCheckFailsCipherCheckPasses(@NonNull List<String> lines) {
      super(lines);
    }
  }

  public static final class DatabaseCorruptedError_NormalCheckPassesCipherCheckFails extends CustomTraceError {
    DatabaseCorruptedError_NormalCheckPassesCipherCheckFails(@NonNull List<String> lines) {
      super(lines);
    }
  }
}
