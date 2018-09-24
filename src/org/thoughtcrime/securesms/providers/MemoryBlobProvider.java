package org.thoughtcrime.securesms.providers;

import android.content.ContentUris;
import android.net.Uri;
import android.support.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class MemoryBlobProvider {

  @SuppressWarnings("unused")
  private static final String TAG = MemoryBlobProvider.class.getSimpleName();

  public  static final String AUTHORITY   = "org.thoughtcrime.securesms";
  public  static final String PATH        = "memory/*/#";
  private static final Uri    CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/memory");

  private final Map<Long, Entry> cache = new HashMap<>();

  private static final MemoryBlobProvider instance = new MemoryBlobProvider();

  public static MemoryBlobProvider getInstance() {
    return instance;
  }

  private MemoryBlobProvider() {}

  public synchronized Uri createSingleUseUri(@NonNull byte[] blob) {
    return createUriInternal(blob, true);
  }

  public synchronized Uri createUri(@NonNull byte[] blob) {
    return createUriInternal(blob, false);
  }

  public synchronized void delete(@NonNull Uri uri) {
    cache.remove(ContentUris.parseId(uri));
  }

  public synchronized @NonNull InputStream getStream(long id) throws IOException {
    Entry entry = cache.get(id);

    if (entry == null) {
      throw new IOException("ID not found: " + id);
    }

    if (entry.isSingleUse()) {
      cache.remove(id);
    }

    return new ByteArrayInputStream(entry.getBlob());
  }

  private Uri createUriInternal(@NonNull byte[] blob, boolean singleUse) {
    try {
      long id = Math.abs(SecureRandom.getInstance("SHA1PRNG").nextLong());
      cache.put(id, new Entry(blob, singleUse));

      Uri uniqueUri = Uri.withAppendedPath(CONTENT_URI, String.valueOf(System.currentTimeMillis()));
      return ContentUris.withAppendedId(uniqueUri, id);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static class Entry {

    private final byte[]  blob;
    private final boolean singleUse;

    private Entry(@NonNull byte[] blob, boolean singleUse) {
      this.blob      = blob;
      this.singleUse = singleUse;
    }

    public byte[] getBlob() {
      return blob;
    }

    public boolean isSingleUse() {
      return singleUse;
    }
  }
}
