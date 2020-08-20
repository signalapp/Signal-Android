package org.thoughtcrime.securesms.groups.v2;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.Util;

import java.util.Arrays;

public final class GroupLinkPassword {

  private static final int SIZE = 16;

  private final byte[] bytes;

  public static @NonNull GroupLinkPassword createNew() {
    return new GroupLinkPassword(Util.getSecretBytes(SIZE));
  }

  public static @NonNull GroupLinkPassword fromBytes(@NonNull byte[] bytes) {
    return new GroupLinkPassword(bytes);
  }

  private GroupLinkPassword(@NonNull byte[] bytes) {
    this.bytes = bytes;
  }

  public @NonNull byte[] serialize() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof GroupLinkPassword)) {
      return false;
    }

    return Arrays.equals(bytes, ((GroupLinkPassword) other).bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}
