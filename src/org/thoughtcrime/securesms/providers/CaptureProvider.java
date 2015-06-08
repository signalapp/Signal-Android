package org.thoughtcrime.securesms.providers;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;

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
    long         id     = System.currentTimeMillis();
    OutputStream output = new EncryptingPartOutputStream(getFile(id), masterSecret);
    bitmap.compress(CompressFormat.JPEG, 100, output);
    output.close();
    return ContentUris.withAppendedId(CONTENT_URI, id);
  }

  public boolean delete(Uri uri) {
    return getFile(ContentUris.parseId(uri)).delete();
  }

  public InputStream getStream(MasterSecret masterSecret, long id) throws IOException {
    return new DecryptingPartInputStream(getFile(id), masterSecret);
  }

  private File getFile(long id) {
    return new File(context.getDir("captures", Context.MODE_PRIVATE), id + ".capture");
  }
}
