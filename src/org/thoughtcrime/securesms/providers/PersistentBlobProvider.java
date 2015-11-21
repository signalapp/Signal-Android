package org.thoughtcrime.securesms.providers;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.EncryptingPartOutputStream;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PersistentBlobProvider {

  private static final String TAG = PersistentBlobProvider.class.getSimpleName();

  private static final String     URI_STRING        = "content://org.thoughtcrime.securesms/capture";
  public  static final Uri        CONTENT_URI       = Uri.parse(URI_STRING);
  public  static final String     AUTHORITY         = "org.thoughtcrime.securesms";
  public  static final String     EXPECTED_PATH     = "capture/*/#";
  private static final String     BLOB_DIRECTORY    = "captures";
  private static final String     DEFAULT_EXTENSION = "blob";
  private static final int        MATCH             = 1;
  private static final UriMatcher MATCHER           = new UriMatcher(UriMatcher.NO_MATCH) {{
    addURI(AUTHORITY, EXPECTED_PATH, MATCH);
  }};

  private static volatile PersistentBlobProvider instance;

  public static PersistentBlobProvider getInstance(Context context) {
    if (instance == null) {
      synchronized (PersistentBlobProvider.class) {
        if (instance == null) {
          instance = new PersistentBlobProvider(context);
        }
      }
    }
    return instance;
  }

  private final Context           context;
  private final Map<Long, byte[]> cache    = Collections.synchronizedMap(new HashMap<Long, byte[]>());
  private final ExecutorService   executor = Executors.newCachedThreadPool();

  private PersistentBlobProvider(Context context) {
    this.context = context.getApplicationContext();
  }

  public Uri create(@NonNull MasterSecret masterSecret,
                    @NonNull byte[] blobBytes,
                    @NonNull String mimeType)
  {
    final long id = System.currentTimeMillis();
    cache.put(id, blobBytes);
    return create(masterSecret, new ByteArrayInputStream(blobBytes), id, mimeType);
  }

  public Uri create(@NonNull MasterSecret masterSecret,
                    @NonNull InputStream input,
                    @NonNull String mimeType)
  {
    return create(masterSecret, input, System.currentTimeMillis(), mimeType);
  }

  private Uri create(MasterSecret masterSecret, InputStream input, long id, String mimeType) {
    persistToDisk(masterSecret, id, input, mimeType);
    final Uri uniqueUri = Uri.withAppendedPath(CONTENT_URI, String.valueOf(System.currentTimeMillis()));
    return ContentUris.withAppendedId(uniqueUri, id);
  }

  private void persistToDisk(final MasterSecret masterSecret,
                             final long id,
                             final InputStream input,
                             final String mimeType)
  {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          OutputStream output = new EncryptingPartOutputStream(getNewFile(id, mimeType), masterSecret);
          Log.w(TAG, "Starting stream copy....");
          Util.copy(input, output);
          Log.w(TAG, "Stream copy finished...");
        } catch (IOException e) {
          Log.w(TAG, e);
        }

        cache.remove(id);
      }
    });
  }

  public Uri createForExternal(@NonNull String mimeType) throws IOException {
    return Uri.fromFile(new File(getExternalDir(context),
                        String.valueOf(System.currentTimeMillis()) + "." + getExtensionFromMimeType(mimeType)))
              .buildUpon()
              .build();
  }

  public boolean delete(@NonNull Uri uri) {
    switch (MATCHER.match(uri)) {
    case MATCH:
      long id = ContentUris.parseId(uri);
      cache.remove(id);
      final File file = getFile(ContentUris.parseId(uri));
      return (file != null) && file.delete();
    default:
      return new File(uri.getPath()).delete();
    }
  }

  public @NonNull InputStream getStream(MasterSecret masterSecret, long id) throws IOException {
    final byte[] cached = cache.get(id);
    return cached != null ? new ByteArrayInputStream(cached)
                          : new DecryptingPartInputStream(getFile(id), masterSecret);
  }

  public @Nullable String getMimeType(Uri uri) {
    final File file   = getFile(ContentUris.parseId(uri));
    if (file == null) return null;

    final String path = file.getAbsolutePath();
    final int i       = path.lastIndexOf(".");
    if (i > 0) {
      final String extension = path.substring(i + 1);
      return MediaUtil.getCorrectedMimeType(
          MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase()));
    } else {
      Log.w(TAG, "getMimeType: file extension not found, uri=" + uri);
      return null;
    }
  }

  private File getNewFile(long id, String mimeType) {
    return new File(context.getDir(BLOB_DIRECTORY, Context.MODE_PRIVATE),
                    id + "." + getExtensionFromMimeType(mimeType));
  }

  private String getExtensionFromMimeType(String mimeType) {
    final String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
    return extension != null ? extension : DEFAULT_EXTENSION;
  }

  private @Nullable File getFile(final long id) {
    final File dir = context.getDir(BLOB_DIRECTORY, Context.MODE_PRIVATE);
    final File[] files = dir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String filename) {
        return filename.startsWith(Long.toString(id));
      }
    });

    if (files.length > 0) {
      return files[0];
    } else {
      Log.w(TAG, "getFile: file not found, id=" + id);
      return null;
    }
  }

  private static @NonNull File getExternalDir(Context context) throws IOException {
    final File externalDir = context.getExternalFilesDir(null);
    if (externalDir == null) throw new IOException("no external files directory");
    return externalDir;
  }

  public static boolean isAuthority(@NonNull Context context, @NonNull Uri uri) {
    try {
      return MATCHER.match(uri) == MATCH || uri.getPath().startsWith(getExternalDir(context).getAbsolutePath());
    } catch (IOException ioe) {
      return false;
    }
  }

}
