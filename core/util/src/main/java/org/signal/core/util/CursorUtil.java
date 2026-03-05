package org.signal.core.util;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;
import java.util.function.Function;


public final class CursorUtil {

  private CursorUtil() {}

  public static String requireString(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.getString(cursor.getColumnIndexOrThrow(column));
  }

  public static int requireInt(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.getInt(cursor.getColumnIndexOrThrow(column));
  }

  public static float requireFloat(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.getFloat(cursor.getColumnIndexOrThrow(column));
  }

  public static long requireLong(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.getLong(cursor.getColumnIndexOrThrow(column));
  }

  public static boolean requireBoolean(@NonNull Cursor cursor, @NonNull String column) {
    return requireInt(cursor, column) != 0;
  }

  public static byte[] requireBlob(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.getBlob(cursor.getColumnIndexOrThrow(column));
  }

  public static boolean isNull(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.isNull(cursor.getColumnIndexOrThrow(column));
  }

  public static boolean requireMaskedBoolean(@NonNull Cursor cursor, @NonNull String column, int position) {
    return Bitmask.read(requireLong(cursor, column), position);
  }

  public static int requireMaskedInt(@NonNull Cursor cursor, @NonNull String column, int position, int flagBitSize) {
    return Conversions.toIntExact(Bitmask.read(requireLong(cursor, column), position, flagBitSize));
  }

  public static Optional<String> getString(@NonNull Cursor cursor, @NonNull String column) {
    if (cursor.getColumnIndex(column) < 0) {
      return Optional.empty();
    } else {
      return Optional.ofNullable(requireString(cursor, column));
    }
  }

  public static Optional<Integer> getInt(@NonNull Cursor cursor, @NonNull String column) {
    if (cursor.getColumnIndex(column) < 0) {
      return Optional.empty();
    } else {
      return Optional.of(requireInt(cursor, column));
    }
  }

  public static Optional<Long> getLong(@NonNull Cursor cursor, @NonNull String column) {
    if (cursor.getColumnIndex(column) < 0) {
      return Optional.empty();
    } else {
      return Optional.of(requireLong(cursor, column));
    }
  }

  public static Optional<Boolean> getBoolean(@NonNull Cursor cursor, @NonNull String column) {
    if (cursor.getColumnIndex(column) < 0) {
      return Optional.empty();
    } else {
      return Optional.of(requireBoolean(cursor, column));
    }
  }

  public static Optional<byte[]> getBlob(@NonNull Cursor cursor, @NonNull String column) {
    if (cursor.getColumnIndex(column) < 0) {
      return Optional.empty();
    } else {
      return Optional.ofNullable(requireBlob(cursor, column));
    }
  }

  /**
   * Reads each column as a string, and concatenates them together into a single string separated by |
   */
  public static String readRowAsString(@NonNull Cursor cursor) {
    StringBuilder row = new StringBuilder();

    for (int i = 0, len = cursor.getColumnCount(); i < len; i++) {
      row.append(cursor.getString(i));
      if (i < len - 1) {
        row.append(" | ");
      }
    }

    return row.toString();
  }
}
