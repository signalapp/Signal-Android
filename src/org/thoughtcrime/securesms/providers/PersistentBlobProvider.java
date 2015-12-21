package org.thoughtcrime.securesms.providers;

import android.annotation.SuppressLint;
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
import org.thoughtcrime.securesms.util.Util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PersistentBlobProvider {

  private static final String TAG = PersistentBlobProvider.class.getSimpleName();

  private static final String     URI_STRING            = "content://org.thoughtcrime.securesms/capture";
  public  static final Uri        CONTENT_URI           = Uri.parse(URI_STRING);
  public  static final String     AUTHORITY             = "org.thoughtcrime.securesms";
  public  static final String     EXPECTED_PATH         = "capture/*/*/#";
  private static final int        MIMETYPE_PATH_SEGMENT = 1;
  private static final String     BLOB_DIRECTORY        = "captures";
  private static final String     BLOB_EXTENSION        = "blob";
  private static final int        MATCH                 = 1;
  private static final UriMatcher MATCHER               = new UriMatcher(UriMatcher.NO_MATCH) {{
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
  @SuppressLint("UseSparseArrays")
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
    persistToDisk(masterSecret, id, input);
    final Uri uniqueUri = CONTENT_URI.buildUpon()
                                     .appendPath(mimeType)
                                     .appendEncodedPath(String.valueOf(System.currentTimeMillis()))
                                     .build();
    return ContentUris.withAppendedId(uniqueUri, id);
  }

  private void persistToDisk(final MasterSecret masterSecret, final long id, final InputStream input) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        try {
          OutputStream output = new EncryptingPartOutputStream(getFile(id), masterSecret);
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
                        String.valueOf(System.currentTimeMillis()) + "." + getExtensionFromMimeType(mimeType)));
  }

  public boolean delete(@NonNull Uri uri) {
    switch (MATCHER.match(uri)) {
    case MATCH:
      long id = ContentUris.parseId(uri);
      cache.remove(id);
      return getFile(ContentUris.parseId(uri)).delete();
    default:
      return new File(uri.getPath()).delete();
    }
  }

  public @NonNull InputStream getStream(MasterSecret masterSecret, long id) throws IOException {
    final byte[] cached = cache.get(id);
    return cached != null ? new ByteArrayInputStream(cached)
                          : new DecryptingPartInputStream(getFile(id), masterSecret);
  }

  private File getFile(long id) {
    return new File(context.getDir(BLOB_DIRECTORY, Context.MODE_PRIVATE), id + "." + BLOB_EXTENSION);
  }

  public static @Nullable String getMimeType(@NonNull Context context, @NonNull Uri persistentBlobUri) {
    if (!isAuthority(context, persistentBlobUri)) return null;
    return persistentBlobUri.getPathSegments().get(MIMETYPE_PATH_SEGMENT);
  }

  private static @NonNull String getExtensionFromMimeType(String mimeType) {
    final String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
    return extension != null ? extension : BLOB_EXTENSION;
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
