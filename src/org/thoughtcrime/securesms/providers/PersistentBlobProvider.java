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
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.InvalidMessageException;

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

  private static final String     URI_STRING            = "content://org.thoughtcrime.securesms/capture-new";
  public  static final Uri        CONTENT_URI           = Uri.parse(URI_STRING);
  public  static final String     AUTHORITY             = "org.thoughtcrime.securesms";
  public  static final String     EXPECTED_PATH_OLD     = "capture/*/*/#";
  public  static final String     EXPECTED_PATH_NEW     = "capture-new/*/*/*/*/#";

  private static final int        MIMETYPE_PATH_SEGMENT = 1;
  private static final int        FILENAME_PATH_SEGMENT = 2;
  private static final int        FILESIZE_PATH_SEGMENT = 3;

  private static final String     BLOB_EXTENSION        = "blob";
  private static final int        MATCH_OLD             = 1;
  private static final int        MATCH_NEW             = 2;

  private static final UriMatcher MATCHER               = new UriMatcher(UriMatcher.NO_MATCH) {{
    addURI(AUTHORITY, EXPECTED_PATH_OLD, MATCH_OLD);
    addURI(AUTHORITY, EXPECTED_PATH_NEW, MATCH_NEW);
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

  public Uri create(@NonNull  MasterSecret masterSecret,
                    @NonNull  byte[] blobBytes,
                    @NonNull  String mimeType,
                    @Nullable String fileName)
  {
    final long id = System.currentTimeMillis();
    cache.put(id, blobBytes);
    return create(masterSecret, new ByteArrayInputStream(blobBytes), id, mimeType, fileName, (long) blobBytes.length);
  }

  public Uri create(@NonNull  MasterSecret masterSecret,
                    @NonNull  InputStream input,
                    @NonNull  String mimeType,
                    @Nullable String fileName,
                    @Nullable Long   fileSize)
  {
    return create(masterSecret, input, System.currentTimeMillis(), mimeType, fileName, fileSize);
  }

  private Uri create(@NonNull  MasterSecret masterSecret,
                     @NonNull  InputStream input,
                               long id,
                     @NonNull  String mimeType,
                     @Nullable String fileName,
                     @Nullable Long fileSize)
  {
    persistToDisk(masterSecret, id, input);
    final Uri uniqueUri = CONTENT_URI.buildUpon()
                                     .appendPath(mimeType)
                                     .appendPath(getEncryptedFileName(masterSecret, fileName))
                                     .appendEncodedPath(String.valueOf(fileSize))
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
    case MATCH_OLD:
    case MATCH_NEW:
      long id = ContentUris.parseId(uri);
      cache.remove(id);
      return getFile(ContentUris.parseId(uri)).delete();
    }

    if (isExternalBlobUri(context, uri)) {
      return new File(uri.getPath()).delete();
    }

    return false;
  }

  public @NonNull InputStream getStream(MasterSecret masterSecret, long id) throws IOException {
    final byte[] cached = cache.get(id);
    return cached != null ? new ByteArrayInputStream(cached)
                          : DecryptingPartInputStream.createFor(masterSecret, getFile(id));
  }

  private File getFile(long id) {
    File legacy = getLegacyFile(id);
    File cache  = getCacheFile(id);

    if (legacy.exists()) return legacy;
    else                 return cache;
  }

  private File getLegacyFile(long id) {
    return new File(context.getDir("captures", Context.MODE_PRIVATE), id + "." + BLOB_EXTENSION);
  }

  private File getCacheFile(long id) {
    return new File(context.getCacheDir(), "capture-" + id + "." + BLOB_EXTENSION);
  }

  private @Nullable String getEncryptedFileName(@NonNull MasterSecret masterSecret, @Nullable String fileName) {
    if (fileName == null) return null;
    return new MasterCipher(masterSecret).encryptBody(fileName);
  }

  public static @Nullable String getMimeType(@NonNull Context context, @NonNull Uri persistentBlobUri) {
    if (!isAuthority(context, persistentBlobUri)) return null;
    return isExternalBlobUri(context, persistentBlobUri)
        ? getMimeTypeFromExtension(persistentBlobUri)
        : persistentBlobUri.getPathSegments().get(MIMETYPE_PATH_SEGMENT);
  }

  public static @Nullable String getFileName(@NonNull Context context, @NonNull MasterSecret masterSecret, @NonNull Uri persistentBlobUri) {
    if (!isAuthority(context, persistentBlobUri))      return null;
    if (isExternalBlobUri(context, persistentBlobUri)) return null;
    if (MATCHER.match(persistentBlobUri) == MATCH_OLD) return null;

    String fileName = persistentBlobUri.getPathSegments().get(FILENAME_PATH_SEGMENT);

    try {
      return new MasterCipher(masterSecret).decryptBody(fileName);
    } catch (InvalidMessageException e) {
      Log.w(TAG, "No valid filename for URI");
    }

    return null;
  }

  public static @Nullable Long getFileSize(@NonNull Context context, Uri persistentBlobUri) {
    if (!isAuthority(context, persistentBlobUri))      return null;
    if (isExternalBlobUri(context, persistentBlobUri)) return null;
    if (MATCHER.match(persistentBlobUri) == MATCH_OLD) return null;

    try {
      return Long.valueOf(persistentBlobUri.getPathSegments().get(FILESIZE_PATH_SEGMENT));
    } catch (NumberFormatException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private static @NonNull String getExtensionFromMimeType(String mimeType) {
    final String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
    return extension != null ? extension : BLOB_EXTENSION;
  }

  private static @NonNull String getMimeTypeFromExtension(@NonNull Uri uri) {
    final String mimeType = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()));
    return mimeType != null ? mimeType : "application/octet-stream";
  }

  private static @NonNull File getExternalDir(Context context) throws IOException {
    final File externalDir = context.getExternalCacheDir();
    if (externalDir == null) throw new IOException("no external files directory");
    return externalDir;
  }

  public static boolean isAuthority(@NonNull Context context, @NonNull Uri uri) {
    int matchResult = MATCHER.match(uri);
    return matchResult == MATCH_NEW || matchResult == MATCH_OLD || isExternalBlobUri(context, uri);
  }

  private static boolean isExternalBlobUri(@NonNull Context context, @NonNull Uri uri) {
    try {
      return uri.getPath().startsWith(getExternalDir(context).getAbsolutePath());
    } catch (IOException ioe) {
      return false;
    }
  }
}
