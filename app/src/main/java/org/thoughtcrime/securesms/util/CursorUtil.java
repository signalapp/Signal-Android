package org.thoughtcrime.securesms.util;

import android.database.Cursor;

import androidx.annotation.NonNull;

import org.whispersystems.libsignal.util.guava.Optional;

public final class CursorUtil {

  private CursorUtil() {}

  public static String requireString(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.getString(cursor.getColumnIndexOrThrow(column));
  }

  public static int requireInt(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.getInt(cursor.getColumnIndexOrThrow(column));
  }

  public static long requireLong(@NonNull Cursor cursor, @NonNull String column) {
    return cursor.getLong(cursor.getColumnIndexOrThrow(column));
  }

  public static boolean requireBoolean(@NonNull Cursor cursor, @NonNull String column) {
    return requireInt(cursor, column) != 0;
  }

  public static Optional<String> getString(@NonNull Cursor cursor, @NonNull String column) {
    if (cursor.getColumnIndex(column) < 0) {
      return Optional.absent();
    } else {
      return Optional.fromNullable(requireString(cursor, column));
    }
  }

  public static Optional<Integer> getInt(@NonNull Cursor cursor, @NonNull String column) {
    if (cursor.getColumnIndex(column) < 0) {
      return Optional.absent();
    } else {
      return Optional.of(requireInt(cursor, column));
    }
  }
}
