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

import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.crypto.ClassicDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.util.Conversions;
import org.thoughtcrime.securesms.util.FileProviderUtil;
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

  @SuppressLint("UseSparseArrays")
  private final Map<Long, byte[]> cache    = Collections.synchronizedMap(new HashMap<Long, byte[]>());
  private final ExecutorService   executor = Executors.newCachedThreadPool();

  private final AttachmentSecret  attachmentSecret;

  private PersistentBlobProvider(@NonNull Context context) {
    this.attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
  }

  public Uri create(@NonNull Context context,
                    @NonNull  byte[] blobBytes,
                    @NonNull  String mimeType,
                    @Nullable String fileName)
  {
    final long id = System.currentTimeMillis();
    cache.put(id, blobBytes);
    return create(context, attachmentSecret, new ByteArrayInputStream(blobBytes), id, mimeType, fileName, (long) blobBytes.length);
  }

  public Uri create(@NonNull Context context,
                    @NonNull  InputStream input,
                    @NonNull  String mimeType,
                    @Nullable String fileName,
                    @Nullable Long   fileSize)
  {
    return create(context, attachmentSecret, input, System.currentTimeMillis(), mimeType, fileName, fileSize);
  }

  private Uri create(@NonNull Context context,
                     @NonNull  AttachmentSecret attachmentSecret,
                     @NonNull  InputStream input,
                               long id,
                     @NonNull  String mimeType,
                     @Nullable String fileName,
                     @Nullable Long fileSize)
  {
    persistToDisk(context, attachmentSecret, id, input);
    final Uri uniqueUri = CONTENT_URI.buildUpon()
                                     .appendPath(mimeType)
                                     .appendPath(fileName)
                                     .appendEncodedPath(String.valueOf(fileSize))
                                     .appendEncodedPath(String.valueOf(System.currentTimeMillis()))
                                     .build();
    return ContentUris.withAppendedId(uniqueUri, id);
  }

  private void persistToDisk(@NonNull Context context,
                             @NonNull AttachmentSecret attachmentSecret,
                             final long id, final InputStream input)
  {
    executor.submit(() -> {
      try {
        OutputStream output = ModernEncryptingPartOutputStream.createFor(attachmentSecret, Conversions.longToByteArray(id), getFile(context, id).file);
        Util.copy(input, output);
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      cache.remove(id);
    });
  }

  public Uri createForExternal(@NonNull Context context, @NonNull String mimeType) throws IOException {
    File target = new File(getExternalDir(context), String.valueOf(System.currentTimeMillis()) + "." + getExtensionFromMimeType(mimeType));
    return FileProviderUtil.getUriFor(context, target);
  }

  public boolean delete(@NonNull Context context, @NonNull Uri uri) {
    switch (MATCHER.match(uri)) {
    case MATCH_OLD:
    case MATCH_NEW:
      long id = ContentUris.parseId(uri);
      cache.remove(id);
      return getFile(context, ContentUris.parseId(uri)).file.delete();
    }

    //noinspection SimplifiableIfStatement
    if (isExternalBlobUri(context, uri)) {
      return new File(uri.getPath()).delete();
    }

    return false;
  }

  public @NonNull InputStream getStream(@NonNull Context context, long id) throws IOException {
    final byte[] cached = cache.get(id);

    if (cached != null) {
      return new ByteArrayInputStream(cached);
    }

    FileData fileData = getFile(context, id);

    if (fileData.modern) return ModernDecryptingPartInputStream.createFor(attachmentSecret, Conversions.longToByteArray(id), fileData.file);
    else                 return ClassicDecryptingPartInputStream.createFor(attachmentSecret, fileData.file);
  }

  private FileData getFile(@NonNull Context context, long id) {
    File legacy      = getLegacyFile(context, id);
    File cache       = getCacheFile(context, id);
    File modernCache = getModernCacheFile(context, id);

    if      (legacy.exists()) return new FileData(legacy, false);
    else if (cache.exists())  return new FileData(cache, false);
    else                      return new FileData(modernCache, true);
  }

  private File getLegacyFile(@NonNull Context context, long id) {
    return new File(context.getDir("captures", Context.MODE_PRIVATE), id + "." + BLOB_EXTENSION);
  }

  private File getCacheFile(@NonNull Context context, long id) {
    return new File(context.getCacheDir(), "capture-" + id + "." + BLOB_EXTENSION);
  }

  private File getModernCacheFile(@NonNull Context context, long id) {
    return new File(context.getCacheDir(), "capture-m-" + id + "." + BLOB_EXTENSION);
  }

  public static @Nullable String getMimeType(@NonNull Context context, @NonNull Uri persistentBlobUri) {
    if (!isAuthority(context, persistentBlobUri)) return null;
    return isExternalBlobUri(context, persistentBlobUri)
        ? getMimeTypeFromExtension(persistentBlobUri)
        : persistentBlobUri.getPathSegments().get(MIMETYPE_PATH_SEGMENT);
  }

  public static @Nullable String getFileName(@NonNull Context context, @NonNull Uri persistentBlobUri) {
    if (!isAuthority(context, persistentBlobUri))      return null;
    if (isExternalBlobUri(context, persistentBlobUri)) return null;
    if (MATCHER.match(persistentBlobUri) == MATCH_OLD) return null;

    return persistentBlobUri.getPathSegments().get(FILENAME_PATH_SEGMENT);
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

  private static class FileData {
    private final File    file;
    private final boolean modern;

    private FileData(File file, boolean modern) {
      this.file   = file;
      this.modern = modern;
    }
  }
}
