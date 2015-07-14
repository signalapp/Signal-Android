package org.thoughtcrime.securesms.providers;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Environment;

import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.EncryptingPartOutputStream;
import org.thoughtcrime.securesms.crypto.MasterSecret;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CaptureProvider {
  private static final String TAG           = CaptureProvider.class.getSimpleName();
  private static final String URI_STRING    = "content://org.thoughtcrime.securesms/capture";
  public  static final Uri    CONTENT_URI   = Uri.parse(URI_STRING);
  public  static final String AUTHORITY     = "org.thoughtcrime.securesms";
  public  static final String EXPECTED_PATH = "capture/#";
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

  private CaptureProvider(Context context) {
    this.context = context.getApplicationContext();
  }

  public Uri create(MasterSecret masterSecret, Bitmap bitmap) throws IOException {
    final long         id     = generateId();
    final OutputStream output = new EncryptingPartOutputStream(getFile(id), masterSecret);
    bitmap.compress(CompressFormat.JPEG, 100, output);
    output.close();
    return ContentUris.withAppendedId(CONTENT_URI, id);
  }

  public Uri createForExternal() throws IOException {
    final File externalDir = context.getExternalFilesDir(null);
    if (externalDir == null) throw new IOException("no external files directory");
    return Uri.fromFile(File.createTempFile(String.valueOf(generateId()), ".jpg", externalDir));
  }

  public boolean delete(Uri uri) {
    switch (uriMatcher.match(uri)) {
    case MATCH: return getFile(ContentUris.parseId(uri)).delete();
    default:    return new File(uri.getPath()).delete();
    }
  }

  public InputStream getStream(MasterSecret masterSecret, long id) throws IOException {
    return new DecryptingPartInputStream(getFile(id), masterSecret);
  }

  private long generateId() {
    return System.currentTimeMillis();
  }

  private File getFile(long id) {
    return new File(context.getDir("captures", Context.MODE_PRIVATE), String.valueOf(id + ".jpg"));
  }
}
