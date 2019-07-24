package org.thoughtcrime.securesms.providers;

import android.app.Application;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Allows for the creation and retrieval of blobs.
 */
public class BlobProvider {

  private static final String TAG = BlobProvider.class.getSimpleName();

  private static final String MULTI_SESSION_DIRECTORY  = "multi_session_blobs";
  private static final String SINGLE_SESSION_DIRECTORY = "single_session_blobs";

  public static final Uri        CONTENT_URI = Uri.parse("content://org.thoughtcrime.securesms/blob");
  public static final String     AUTHORITY   = "org.thoughtcrime.securesms";
  public static final String     PATH        = "blob/*/*/*/*/*";

  private static final int STORAGE_TYPE_PATH_SEGMENT = 1;
  private static final int MIMETYPE_PATH_SEGMENT     = 2;
  private static final int FILENAME_PATH_SEGMENT     = 3;
  private static final int FILESIZE_PATH_SEGMENT     = 4;
  private static final int ID_PATH_SEGMENT           = 5;

  private static final int        MATCH       = 1;
  private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH) {{
    addURI(AUTHORITY, PATH, MATCH);
  }};

  private static final BlobProvider INSTANCE = new BlobProvider();

  private final Map<Uri, byte[]> memoryBlobs = new HashMap<>();


  public static BlobProvider getInstance() {
    return INSTANCE;
  }

  /**
   * Begin building a blob for the provided data. Allows for the creation of in-memory blobs.
   */
  public MemoryBlobBuilder forData(@NonNull byte[] data) {
    return new MemoryBlobBuilder(data);
  }

  /**
   * Begin building a blob for the provided input stream.
   */
  public BlobBuilder forData(@NonNull InputStream data, long fileSize) {
    return new BlobBuilder(data, fileSize);
  }

  /**
   * Retrieve a stream for the content with the specified URI.
   * @throws IOException If the stream fails to open or the spec of the URI doesn't match.
   */
  public synchronized @NonNull InputStream getStream(@NonNull Context context, @NonNull Uri uri) throws IOException {
    if (isAuthority(uri)) {
      StorageType storageType = StorageType.decode(uri.getPathSegments().get(STORAGE_TYPE_PATH_SEGMENT));

      if (storageType.isMemory()) {
        byte[] data = memoryBlobs.get(uri);

        if (data != null) {
          if (storageType == StorageType.SINGLE_USE_MEMORY) {
            memoryBlobs.remove(uri);
          }
          return new ByteArrayInputStream(data);
        } else {
          throw new IOException("Failed to find in-memory blob for: " + uri);
        }
      } else {
        String id        = uri.getPathSegments().get(ID_PATH_SEGMENT);
        String directory = getDirectory(storageType);
        File   file      = new File(getOrCreateCacheDirectory(context, directory), buildFileName(id));

        return ModernDecryptingPartInputStream.createFor(AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(), file, 0);
      }
    } else {
      throw new IOException("Provided URI does not match this spec. Uri: " + uri);
    }
  }

  /**
   * Delete the content with the specified URI.
   */
  public synchronized void delete(@NonNull Context context, @NonNull Uri uri) {
    if (!isAuthority(uri)) {
      Log.d(TAG, "Can't delete. Not the authority for uri: " + uri);
      return;
    }

    try {
      StorageType storageType = StorageType.decode(uri.getPathSegments().get(STORAGE_TYPE_PATH_SEGMENT));

      if (storageType.isMemory()) {
        memoryBlobs.remove(uri);
      } else {
        String id        = uri.getPathSegments().get(ID_PATH_SEGMENT);
        String directory = getDirectory(storageType);
        File   file      = new File(getOrCreateCacheDirectory(context, directory), buildFileName(id));

        if (!file.delete()) {
          throw new IOException("File wasn't deleted.");
        }
      }
    } catch (IOException e) {
      Log.w(TAG, "Failed to delete uri: " + uri, e);
    }
  }

  /**
   * Indicates a new app session has started, allowing old single-session blobs to be deleted.
   */
  public synchronized void onSessionStart(@NonNull Context context) {
    File directory = getOrCreateCacheDirectory(context, SINGLE_SESSION_DIRECTORY);
    for (File file : directory.listFiles()) {
      file.delete();
    }
  }

  public static @Nullable String getMimeType(@NonNull Uri uri) {
    if (isAuthority(uri)) {
      return uri.getPathSegments().get(MIMETYPE_PATH_SEGMENT);
    }
    return null;
  }

  public static @Nullable String getFileName(@NonNull Uri uri) {
    if (isAuthority(uri)) {
      return uri.getPathSegments().get(FILENAME_PATH_SEGMENT);
    }
    return null;
  }

  public static @Nullable Long getFileSize(@NonNull Uri uri) {
    if (isAuthority(uri)) {
      try {
        return Long.parseLong(uri.getPathSegments().get(FILESIZE_PATH_SEGMENT));
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  public static boolean isAuthority(@NonNull Uri uri) {
    return URI_MATCHER.match(uri) == MATCH;
  }

  @WorkerThread
  private synchronized @NonNull Uri writeBlobSpecToDisk(@NonNull Context context, @NonNull BlobSpec blobSpec, @Nullable ErrorListener errorListener) throws IOException {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    String           directory        = getDirectory(blobSpec.getStorageType());
    File             outputFile       = new File(getOrCreateCacheDirectory(context, directory), buildFileName(blobSpec.id));
    OutputStream     outputStream     = ModernEncryptingPartOutputStream.createFor(attachmentSecret, outputFile, true).second;

    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        Util.copy(blobSpec.getData(), outputStream);
      } catch (IOException e) {
        if (errorListener != null) {
          errorListener.onError(e);
        }
      }
    });

    return buildUri(blobSpec);
  }

  private synchronized @NonNull Uri writeBlobSpecToMemory(@NonNull BlobSpec blobSpec, @NonNull byte[] data) {
    Uri uri = buildUri(blobSpec);
    memoryBlobs.put(uri, data);
    return uri;
  }

  private static @NonNull String buildFileName(@NonNull String id) {
    return id + ".blob";
  }

  private static @NonNull String getDirectory(@NonNull StorageType storageType) {
    return storageType == StorageType.MULTI_SESSION_DISK ? MULTI_SESSION_DIRECTORY : SINGLE_SESSION_DIRECTORY;
  }

  private static @NonNull Uri buildUri(@NonNull BlobSpec blobSpec) {
    return CONTENT_URI.buildUpon()
                      .appendPath(blobSpec.getStorageType().encode())
                      .appendPath(blobSpec.getMimeType())
                      .appendPath(blobSpec.getFileName())
                      .appendEncodedPath(String.valueOf(blobSpec.getFileSize()))
                      .appendPath(blobSpec.getId())
                      .build();
  }

  private static File getOrCreateCacheDirectory(@NonNull Context context, @NonNull String directory) {
    File file = new File(context.getCacheDir(), directory);
    if (!file.exists()) {
      file.mkdir();
    }

    return file;
  }

  public class BlobBuilder {

    private InputStream data;
    private String      id;
    private String      mimeType;
    private String      fileName;
    private long        fileSize;

    private BlobBuilder(@NonNull InputStream data, long fileSize) {
      this.id       = UUID.randomUUID().toString();
      this.data     = data;
      this.fileSize = fileSize;
    }

    public BlobBuilder withMimeType(@NonNull String mimeType) {
      this.mimeType = mimeType;
      return this;
    }

    public BlobBuilder withFileName(@NonNull String fileName) {
      this.fileName = fileName;
      return this;
    }

    protected BlobSpec buildBlobSpec(@NonNull StorageType storageType) {
      return new BlobSpec(data, id, storageType, mimeType, fileName, fileSize);
    }

    /**
     * Create a blob that will exist for a single app session. An app session is defined as the
     * period from one {@link Application#onCreate()} to the next.
     */
    @WorkerThread
    public Uri createForSingleSessionOnDisk(@NonNull Context context, @Nullable ErrorListener errorListener) throws IOException {
      return writeBlobSpecToDisk(context, buildBlobSpec(StorageType.SINGLE_SESSION_DISK), errorListener);
    }

    /**
     * Create a blob that will exist for multiple app sessions. It is the caller's responsibility to
     * eventually call {@link BlobProvider#delete(Context, Uri)} when the blob is no longer in use.
     */
    @WorkerThread
    public Uri createForMultipleSessionsOnDisk(@NonNull Context context, @Nullable ErrorListener errorListener) throws IOException {
      return writeBlobSpecToDisk(context, buildBlobSpec(StorageType.MULTI_SESSION_DISK), errorListener);
    }
  }

  public class MemoryBlobBuilder extends BlobBuilder {

    private byte[] data;

    private MemoryBlobBuilder(@NonNull byte[] data) {
      super(new ByteArrayInputStream(data), data.length);
      this.data = data;
    }

    @Override
    public MemoryBlobBuilder withMimeType(@NonNull String mimeType) {
      super.withMimeType(mimeType);
      return this;
    }

    @Override
    public MemoryBlobBuilder withFileName(@NonNull String fileName) {
      super.withFileName(fileName);
      return this;
    }

    /**
     * Create a blob that is stored in memory and can only be read a single time. After a single
     * read, it will be removed from storage. Useful for when a Uri is needed to read transient data.
     */
    public Uri createForSingleUseInMemory() {
      return writeBlobSpecToMemory(buildBlobSpec(StorageType.SINGLE_USE_MEMORY), data);
    }

    /**
     * Create a blob that is stored in memory. Will persist for a single app session. You should
     * always try to call {@link BlobProvider#delete(Context, Uri)} after you're done with the blob
     * to free up memory.
     */
    public Uri createForSingleSessionInMemory() {
      return writeBlobSpecToMemory(buildBlobSpec(StorageType.SINGLE_SESSION_MEMORY), data);
    }
  }

  public interface ErrorListener {
    @WorkerThread
    void onError(IOException e);
  }

  private static class BlobSpec {

    private final InputStream data;
    private final String      id;
    private final StorageType storageType;
    private final String      mimeType;
    private final String      fileName;
    private final long        fileSize;

    private BlobSpec(@NonNull InputStream data,
                     @NonNull String id,
                     @NonNull StorageType storageType,
                     @NonNull String mimeType,
                     @Nullable String fileName,
                     @IntRange(from = 0) long fileSize)
    {
      this.data        = data;
      this.id          = id;
      this.storageType = storageType;
      this.mimeType    = mimeType;
      this.fileName    = fileName;
      this.fileSize    = fileSize;
    }

    private @NonNull InputStream getData() {
      return data;
    }

    private @NonNull String getId() {
      return id;
    }

    private @NonNull StorageType getStorageType() {
      return storageType;
    }

    private @NonNull String getMimeType() {
      return mimeType;
    }

    private @Nullable String getFileName() {
      return fileName;
    }

    private long getFileSize() {
      return fileSize;
    }
  }

  private enum StorageType {

    SINGLE_USE_MEMORY("single-use-memory", true),
    SINGLE_SESSION_MEMORY("single-session-memory", true),
    SINGLE_SESSION_DISK("single-session-disk", false),
    MULTI_SESSION_DISK("multi-session-disk", false);

    private final String  encoded;
    private final boolean inMemory;

    StorageType(String encoded, boolean inMemory) {
      this.encoded  = encoded;
      this.inMemory = inMemory;
    }

    private String encode() {
      return encoded;
    }

    private boolean isMemory() {
      return inMemory;
    }

    private static StorageType decode(@NonNull String encoded) throws IOException {
      for (StorageType storageType : StorageType.values()) {
        if (storageType.encoded.equals(encoded)) {
          return storageType;
        }
      }
      throw new IOException("Failed to decode lifespan.");
    }
  }
}
