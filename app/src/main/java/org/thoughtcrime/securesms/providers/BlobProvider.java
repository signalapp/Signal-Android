package org.thoughtcrime.securesms.providers;

import android.app.Application;
import android.content.Context;
import android.content.UriMatcher;
import android.media.MediaDataSource;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.database.DraftTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.util.IOFunction;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.video.ByteArrayMediaDataSource;
import org.thoughtcrime.securesms.video.EncryptedMediaDataSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Allows for the creation and retrieval of blobs.
 */
public class BlobProvider {

  private static final String TAG = Log.tag(BlobProvider.class);

  private static final String DRAFT_ATTACHMENTS_DIRECTORY = "draft_blobs";
  private static final String MULTI_SESSION_DIRECTORY     = "multi_session_blobs";
  private static final String SINGLE_SESSION_DIRECTORY    = "single_session_blobs";

  public static final String AUTHORITY   = BuildConfig.APPLICATION_ID + ".blob";
  public static final Uri    CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/blob");
  public static final String PATH        = "blob/*/*/*/*/*";

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

  private volatile boolean initialized = false;


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
    waitUntilInitialized();
    return getStream(context, uri, 0L);
  }

  /**
   * Retrieve a stream for the content with the specified URI starting from the specified position.
   * @throws IOException If the stream fails to open or the spec of the URI doesn't match.
   */
  public synchronized @NonNull InputStream getStream(@NonNull Context context, @NonNull Uri uri, long position) throws IOException {
    waitUntilInitialized();
    return getBlobRepresentation(context,
                                 uri,
                                 bytes -> {
                                   ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
                                   if (byteArrayInputStream.skip(position) != position) {
                                     throw new IOException("Failed to skip to position " + position + " for: " + uri);
                                   }
                                   return byteArrayInputStream;
                                 },
                                 file -> ModernDecryptingPartInputStream.createFor(getAttachmentSecret(context),
                                                                                   file,
                                                                                   position));
  }

  @RequiresApi(23)
  public synchronized @NonNull MediaDataSource getMediaDataSource(@NonNull Context context, @NonNull Uri uri) throws IOException {
    waitUntilInitialized();
    return getBlobRepresentation(context,
                                 uri,
                                 ByteArrayMediaDataSource::new,
                                 file -> EncryptedMediaDataSource.createForDiskBlob(getAttachmentSecret(context), file));
  }

  private synchronized @NonNull <T> T getBlobRepresentation(@NonNull Context context,
                                                            @NonNull Uri uri,
                                                            @NonNull IOFunction<byte[], T> getByteRepresentation,
                                                            @NonNull IOFunction<File, T> getFileRepresentation)
      throws IOException
  {
    if (isAuthority(uri)) {
      StorageType storageType = StorageType.decode(uri.getPathSegments().get(STORAGE_TYPE_PATH_SEGMENT));

      if (storageType.isMemory()) {
        byte[] data = memoryBlobs.get(uri);

        if (data != null) {
          if (storageType == StorageType.SINGLE_USE_MEMORY) {
            memoryBlobs.remove(uri);
          }
          return getByteRepresentation.apply(data);
        } else {
          throw new IOException("Failed to find in-memory blob for: " + uri);
        }
      } else {
        String id        = uri.getPathSegments().get(ID_PATH_SEGMENT);
        String directory = getDirectory(storageType);
        File   file      = new File(getOrCreateDirectory(context, directory), buildFileName(id));

        return getFileRepresentation.apply(file);
      }
    } else {
      throw new IOException("Provided URI does not match this spec. Uri: " + uri);
    }
  }

  private synchronized AttachmentSecret getAttachmentSecret(@NonNull Context context) {
    return AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
  }

  /**
   * Delete the content with the specified URI.
   */
  public synchronized void delete(@NonNull Context context, @NonNull Uri uri) {
    waitUntilInitialized();

    if (!isAuthority(uri)) {
      Log.d(TAG, "Can't delete. Not the authority for uri: " + uri);
      return;
    }

    Log.d(TAG, "Deleting " + getId(uri));

    try {
      StorageType storageType = StorageType.decode(uri.getPathSegments().get(STORAGE_TYPE_PATH_SEGMENT));

      if (storageType.isMemory()) {
        memoryBlobs.remove(uri);
      } else {
        String id        = uri.getPathSegments().get(ID_PATH_SEGMENT);
        String directory = getDirectory(storageType);
        File   file      = new File(getOrCreateDirectory(context, directory), buildFileName(id));

        if (file.delete()) {
          Log.d(TAG, "Successfully deleted " + getId(uri));
        } else {
          throw new IOException("File wasn't deleted.");
        }
      }
    } catch (IOException e) {
      Log.w(TAG, "Failed to delete uri: " + getId(uri), e);
    }
  }

  /**
   * Allows the class to be initialized. Part of this initialization is deleting any leftover
   * single-session blobs from the previous session. However, this class defers that work to a
   * background thread, so callers don't have to worry about it.
   */
  @AnyThread
  public synchronized void initialize(@NonNull Context context) {
    SignalExecutors.BOUNDED.execute(() -> {
      synchronized (this) {
        File   directory = getOrCreateDirectory(context, SINGLE_SESSION_DIRECTORY);
        File[] files     = directory.listFiles();

        if (files != null) {
          for (File file : files) {
            if (file.delete()) {
              Log.d(TAG, "Deleted single-session file: " + file.getName());
            } else {
              Log.w(TAG, "Failed to delete single-session file! " + file.getName());
            }
          }
        } else {
          Log.w(TAG, "Null directory listing!");
        }

        deleteOrphanedDraftFiles(context);

        Log.i(TAG, "Initialized.");
        initialized = true;
        notifyAll();
      }
    });
  }

  private static void deleteOrphanedDraftFiles(@NonNull Context context) {
    File   directory = getOrCreateDirectory(context, DRAFT_ATTACHMENTS_DIRECTORY);
    File[] files     = directory.listFiles();

    if (files == null || files.length == 0) {
      Log.d(TAG, "No attachment drafts exist. Skipping.");
      return;
    }

    DraftTable        draftDatabase   = SignalDatabase.drafts();
    DraftTable.Drafts voiceNoteDrafts = draftDatabase.getAllVoiceNoteDrafts();

    @SuppressWarnings("ConstantConditions")
    List<String> draftFileNames = voiceNoteDrafts.stream()
                                                 .map(VoiceNoteDraft::fromDraft)
                                                 .map(VoiceNoteDraft::getUri)
                                                 .map(BlobProvider::getId)
                                                 .filter(Objects::nonNull)
                                                 .map(BlobProvider::buildFileName)
                                                 .collect(Collectors.toList());

    for (final File file : files) {
      if (!draftFileNames.contains(file.getName())) {
        if (file.delete()) {
          Log.d(TAG, "Deleted orphaned attachment draft: " + file.getName());
        } else {
          Log.d(TAG, "Failed to delete orphaned attachment draft: " + file.getName());
        }
      }
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

  private static @Nullable String getId(@NonNull Uri uri) {
    if (isAuthority(uri)) {
      return uri.getPathSegments().get(ID_PATH_SEGMENT);
    }
    return null;
  }

  @WorkerThread
  public long calculateFileSize(@NonNull Context context, @NonNull Uri uri) {
    if (!isAuthority(uri)) {
      return 0;
    }

    try (InputStream stream = getStream(context, uri)) {
      return StreamUtil.getStreamLength(stream);
    } catch (IOException e) {
      Log.w(TAG, e);
      return 0;
    }
  }

  public static boolean isAuthority(@NonNull Uri uri) {
    return URI_MATCHER.match(uri) == MATCH;
  }

  @WorkerThread
  private synchronized @NonNull Uri writeBlobSpecToDisk(@NonNull Context context, @NonNull BlobSpec blobSpec)
      throws IOException
  {
    waitUntilInitialized();

    CountDownLatch               latch     = new CountDownLatch(1);
    AtomicReference<IOException> exception = new AtomicReference<>(null);
    Uri                          uri       = writeBlobSpecToDiskAsync(context, blobSpec, latch::countDown, e -> {
                                               exception.set(e);
                                               latch.countDown();
                                             });

    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new IOException(e);
    }

    if (exception.get() != null) {
      throw exception.get();
    }

    return uri;
  }


  @WorkerThread
  private synchronized @NonNull Uri writeBlobSpecToDiskAsync(@NonNull Context context,
                                                             @NonNull BlobSpec blobSpec,
                                                             @Nullable SuccessListener successListener,
                                                             @Nullable ErrorListener errorListener)
      throws IOException
  {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    String           directory        = getDirectory(blobSpec.getStorageType());
    File             outputFile       = new File(getOrCreateDirectory(context, directory), buildFileName(blobSpec.id));
    OutputStream     outputStream     = ModernEncryptingPartOutputStream.createFor(attachmentSecret, outputFile, true).second;

    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        StreamUtil.copy(blobSpec.getData(), outputStream);

        if (successListener != null) {
          successListener.onSuccess();
        }
      } catch (IOException e) {
        Log.w(TAG, "Error during write!", e);
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
    switch (storageType) {
      case SINGLE_USE_MEMORY:
      case SINGLE_SESSION_MEMORY:
        throw new IllegalArgumentException("In-Memory Blobs do not have directories.");
      case SINGLE_SESSION_DISK:
        return SINGLE_SESSION_DIRECTORY;
      case MULTI_SESSION_DISK:
        return MULTI_SESSION_DIRECTORY;
      case ATTACHMENT_DRAFT:
        return DRAFT_ATTACHMENTS_DIRECTORY;
    }
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

  private static File getOrCreateDirectory(@NonNull Context context, @NonNull String directory) {
    return context.getDir(directory, Context.MODE_PRIVATE);
  }

  /**
   * Returns a {@link File} within the appropriate directory to be cleaned up as part of
   * normal operations. Unlike other blobs, this is just a file reference and no
   * automatic encryption occurs when reading or writing and must be done by the caller.
   *
   * @return file located in the appropriate directory to be delete on app session restarts
   */
  public File forNonAutoEncryptingSingleSessionOnDisk(@NonNull Context context) {
    String directory = getDirectory(StorageType.SINGLE_SESSION_DISK);
    String id        = UUID.randomUUID().toString();
    return new File(getOrCreateDirectory(context, directory), buildFileName(id));
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

    public BlobBuilder withFileName(@Nullable String fileName) {
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
    public Uri createForSingleSessionOnDisk(@NonNull Context context) throws IOException {
      return writeBlobSpecToDisk(context, buildBlobSpec(StorageType.SINGLE_SESSION_DISK));
    }

    /**
     * Create a blob that will exist for a single app session. An app session is defined as the
     * period from one {@link Application#onCreate()} to the next. The file will be created on disk
     * synchronously, but the data will copied asynchronously. This is helpful when the copy is
     * long-running, such as in the case of recording a voice note.
     */
    @WorkerThread
    public Uri createForSingleSessionOnDiskAsync(@NonNull Context context,
                                                 @Nullable SuccessListener successListener,
                                                 @Nullable ErrorListener errorListener)
        throws IOException
    {
      return writeBlobSpecToDiskAsync(context, buildBlobSpec(StorageType.SINGLE_SESSION_DISK), successListener, errorListener);
    }

    /**
     * Create a blob that will exist for multiple app sessions. It is the caller's responsibility to
     * eventually call {@link BlobProvider#delete(Context, Uri)} when the blob is no longer in use.
     */
    @WorkerThread
    public Uri createForMultipleSessionsOnDisk(@NonNull Context context) throws IOException {
      return writeBlobSpecToDisk(context, buildBlobSpec(StorageType.MULTI_SESSION_DISK));
    }

    /**
     * Create a blob that will exist for multiple app sessions. The file will be created on disk
     * synchronously, but the data will copied asynchronously. This is helpful when the copy is
     * long-running, such as in the case of recording a voice note.
     *
     * It is the caller's responsibility to eventually call {@link BlobProvider#delete(Context, Uri)}
     * when the blob is no longer in use.
     */
    @WorkerThread
    public Uri createForDraftAttachmentAsync(@NonNull Context context,
                                             @Nullable SuccessListener successListener,
                                             @Nullable ErrorListener errorListener)
        throws IOException
    {
      return writeBlobSpecToDiskAsync(context, buildBlobSpec(StorageType.ATTACHMENT_DRAFT), successListener, errorListener);
    }
  }

  private synchronized void waitUntilInitialized() {
    if (!initialized) {
      Log.i(TAG, "Waiting for initialization...");
      synchronized (this) {
        while (!initialized) {
          Util.wait(this, 0);
        }
        Log.i(TAG, "Initialization complete.");
      }
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

  public interface SuccessListener {
    @WorkerThread
    void onSuccess();
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
    MULTI_SESSION_DISK("multi-session-disk", false),
    ATTACHMENT_DRAFT("attachment-draft", false);

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
