package org.thoughtcrime.securesms.providers;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.MemoryFile;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.util.MemoryFileUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class BlobContentProvider extends BaseContentProvider {

  private static final String TAG = Log.tag(BlobContentProvider.class);

  @Override
  public boolean onCreate() {
    Log.i(TAG, "onCreate()");
    return true;
  }

  @Override
  public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
    Log.i(TAG, "openFile() called: " + uri);

    try {
      try (InputStream stream = BlobProvider.getInstance().getStream(AppDependencies.getApplication(), uri)) {
        Long fileSize = BlobProvider.getFileSize(uri);
        if (fileSize == null) {
          Log.w(TAG, "No file size available");
          throw new FileNotFoundException();
        }

        return getParcelStreamForStream(stream, Util.toIntExact(fileSize));
      }
    } catch (IOException e) {
      throw new FileNotFoundException();
    }
  }

  private static @NonNull ParcelFileDescriptor getParcelStreamForStream(@NonNull InputStream in, int fileSize) throws IOException {
    MemoryFile memoryFile = new MemoryFile(null, fileSize);

    try (OutputStream out = memoryFile.getOutputStream()) {
      StreamUtil.copy(in, out);
    }

    return MemoryFileUtil.getParcelFileDescriptor(memoryFile);
  }

  @Nullable
  @Override
  public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
    Log.i(TAG, "query() called: " + uri);

    String mimeType = BlobProvider.getMimeType(uri);
    String fileName = BlobProvider.getFileName(uri);
    Long   fileSize = BlobProvider.getFileSize(uri);

    if (fileSize == null) {
      Log.w(TAG, "No file size");
      return null;
    }

    if (mimeType == null) {
      Log.w(TAG, "No mime type");
      return null;
    }

    if (fileName == null) {
      fileName = createFileNameForMimeType(mimeType);
    }

    return createCursor(projection, fileName, fileSize);
  }

  @Nullable
  @Override
  public String getType(@NonNull Uri uri) {
    return BlobProvider.getMimeType(uri);
  }

  @Nullable
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
    return null;
  }

  @Override
  public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
    return 0;
  }
}
