package org.thoughtcrime.securesms.providers;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.EncryptingPartOutputStream;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Util;

import java.io.ByteArrayInputStream;
import java.io.File;
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

  private static final String     URI_STRING    = "content://org.thoughtcrime.securesms/capture";
  public  static final Uri        CONTENT_URI   = Uri.parse(URI_STRING);
  public  static final String     AUTHORITY     = "org.thoughtcrime.securesms";
  public  static final String     EXPECTED_PATH = "capture/*/#";
  private static final int        MATCH         = 1;
  private static final UriMatcher MATCHER       = new UriMatcher(UriMatcher.NO_MATCH) {{
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

  private final Context context;
  private final Map<Long, byte[]> cache    = Collections.synchronizedMap(new HashMap<Long, byte[]>());
  private final ExecutorService   executor = Executors.newCachedThreadPool();

  private PersistentBlobProvider(Context context) {
    this.context = context.getApplicationContext();
  }

  public Uri create(@NonNull MasterSecret masterSecret,
                    @NonNull Recipients recipients,
                    @NonNull byte[] imageBytes)
  {
    final long id = generateId(recipients);
    cache.put(id, imageBytes);
    return create(masterSecret, new ByteArrayInputStream(imageBytes), id);
  }

  public Uri create(@NonNull MasterSecret masterSecret,
                    @NonNull InputStream input)
  {
    return create(masterSecret, input, System.currentTimeMillis());
  }

  private Uri create(MasterSecret masterSecret, InputStream input, long id) {
    persistToDisk(masterSecret, id, input);
    final Uri uniqueUri = Uri.withAppendedPath(CONTENT_URI, String.valueOf(System.currentTimeMillis()));
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

  public Uri createForExternal(@NonNull Recipients recipients) throws IOException {
    return Uri.fromFile(new File(getExternalDir(context), String.valueOf(generateId(recipients)) + ".jpg"))
              .buildUpon()
              .appendQueryParameter("unique", String.valueOf(System.currentTimeMillis()))
              .build();
  }

  public boolean delete(@NonNull Uri uri) {
    switch (MATCHER.match(uri)) {
    case MATCH: return getFile(ContentUris.parseId(uri)).delete();
    default:    return new File(uri.getPath()).delete();
    }
  }

  public @NonNull InputStream getStream(MasterSecret masterSecret, long id) throws IOException {
    final byte[] cached = cache.get(id);
    return cached != null ? new ByteArrayInputStream(cached)
                          : new DecryptingPartInputStream(getFile(id), masterSecret);
  }

  private int generateId(Recipients recipients) {
    return Math.abs(Arrays.hashCode(recipients.getIds()));
  }

  private File getFile(long id) {
    return new File(context.getDir("captures", Context.MODE_PRIVATE), id + ".jpg");
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
