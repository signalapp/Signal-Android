package org.thoughtcrime.securesms.giph.model;


import androidx.annotation.NonNull;

import com.bumptech.glide.load.Key;

import org.session.libsession.utilities.Conversions;

import java.security.MessageDigest;

public class ChunkedImageUrl implements Key {

  public static final long SIZE_UNKNOWN = -1;

  private final String url;
  private final long size;

  public ChunkedImageUrl(@NonNull String url) {
    this(url, SIZE_UNKNOWN);
  }

  public ChunkedImageUrl(@NonNull String url, long size) {
    this.url = url;
    this.size = size;
  }

  public String getUrl() {
    return url;
  }

  public long getSize() {
    return size;
  }

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    if (url == null) { return; }
    messageDigest.update(url.getBytes());
    messageDigest.update(Conversions.longToByteArray(size));
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof ChunkedImageUrl)) return false;

    ChunkedImageUrl that = (ChunkedImageUrl)other;

    if (this.url == null || that.url == null) { return false; }

    return this.url.equals(that.url) && this.size == that.size;
  }

  @Override
  public int hashCode() {
    if (url == null) { return 0; }
    return url.hashCode() ^ (int)size;
  }
}
