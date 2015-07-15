package org.thoughtcrime.securesms.providers;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.util.SparseArrayCompat;
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

public class CaptureProvider {
  private static final String TAG           = CaptureProvider.class.getSimpleName();
  private static final String URI_STRING    = "content://org.thoughtcrime.securesms/capture";
  public  static final Uri    CONTENT_URI   = Uri.parse(URI_STRING);
  public  static final String AUTHORITY     = "org.thoughtcrime.securesms";
  public  static final String EXPECTED_PATH = "capture/*/#";
  private static final int    MATCH         = 1;
  public static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH) {{
    addURI(AUTHORITY, EXPECTED_PATH, MATCH);
  }};

  private static volatile CaptureProvider instance;

  public static CaptureProvider getInstance(Context context) {
    if (instance == null) {
      synchronized (CaptureProvider.class) {
        if (instance == null) {
          instance = new CaptureProvider(context);
        }
      }
    }
    return instance;
  }

  private final Context context;
  private final SparseArrayCompat<byte[]> cache = new SparseArrayCompat<>();

  private CaptureProvider(Context context) {
    this.context = context.getApplicationContext();
  }

  public Uri create(@NonNull MasterSecret masterSecret,
                    @NonNull Recipients recipients,
                    @NonNull byte[] imageBytes)
  {
    final int id = generateId(recipients);
    cache.put(id, imageBytes);
    persistToDisk(masterSecret, id, imageBytes);
    final Uri uniqueUri = Uri.withAppendedPath(CONTENT_URI, String.valueOf(System.currentTimeMillis()));
    return ContentUris.withAppendedId(uniqueUri, id);
  }

  private void persistToDisk(final MasterSecret masterSecret, final int id, final byte[] imageBytes) {
    new AsyncTask<Void, Void, Void>() {
      @Override protected Void doInBackground(Void... params) {
        try {
          final OutputStream output = new EncryptingPartOutputStream(getFile(id), masterSecret);
          Util.copy(new ByteArrayInputStream(imageBytes), output);
        } catch (IOException e) {
          Log.w(TAG, e);
        }
        return null;
      }

      @Override protected void onPostExecute(Void aVoid) {
        cache.remove(id);
      }
    }.execute();
  }

  public Uri createForExternal(@NonNull Recipients recipients) throws IOException {
    final File externalDir = context.getExternalFilesDir(null);
    if (externalDir == null) throw new IOException("no external files directory");
    return Uri.fromFile(new File(externalDir, String.valueOf(generateId(recipients)) + ".jpg"))
              .buildUpon()
              .appendQueryParameter("unique", String.valueOf(System.currentTimeMillis()))
              .build();
  }

  public boolean delete(@NonNull Uri uri) {
    switch (uriMatcher.match(uri)) {
    case MATCH: return getFile(ContentUris.parseId(uri)).delete();
    default:    return new File(uri.getPath()).delete();
    }
  }

  public InputStream getStream(MasterSecret masterSecret, long id) throws IOException {
    final byte[] cached = cache.get((int)id);
    return cached != null ? new ByteArrayInputStream(cached)
                          : new DecryptingPartInputStream(getFile(id), masterSecret);
  }

  private int generateId(Recipients recipients) {
    return Math.abs(Arrays.hashCode(recipients.getIds()));
  }

  private File getFile(long id) {
    return new File(context.getDir("captures", Context.MODE_PRIVATE), id + ".jpg");
  }
}
