package org.thoughtcrime.securesms.providers;

import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

public abstract class BaseContentProvider extends ContentProvider {

  private static final String[] COLUMNS = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

  /**
   * Sanity checks the security like FileProvider does.
   */
  @Override
  public void attachInfo(@NonNull Context context, @NonNull ProviderInfo info) {
    super.attachInfo(context, info);

    if (info.exported) {
      throw new SecurityException("Provider must not be exported");
    }
    if (!info.grantUriPermissions) {
      throw new SecurityException("Provider must grant uri permissions");
    }
  }

  protected static Cursor createCursor(@Nullable String[] projection, @NonNull String fileName, long fileSize) {
    if (projection == null || projection.length == 0) {
      projection = COLUMNS;
    }

    ArrayList<String> cols   = new ArrayList<>(projection.length);
    ArrayList<Object> values = new ArrayList<>(projection.length);

    for (String col : projection) {
      if (OpenableColumns.DISPLAY_NAME.equals(col)) {
        cols.add(OpenableColumns.DISPLAY_NAME);
        values.add(fileName);
      } else if (OpenableColumns.SIZE.equals(col)) {
        cols.add(OpenableColumns.SIZE);
        values.add(fileSize);
      }
    }

    MatrixCursor cursor = new MatrixCursor(cols.toArray(new String[0]), 1);

    cursor.addRow(values.toArray(new Object[0]));

    return cursor;
  }

  protected static String createFileNameForMimeType(String mimeType) {
    return mimeType.replace('/', '.');
  }
}
