package org.whispersystems.signalservice.api.subscriptions;


import org.signal.core.util.Base64;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;

import io.reactivex.rxjava3.annotations.NonNull;

/**
 * Id representing a single subscriber.
 */
public final class SubscriberId {

  private static final int SIZE = 32;

  private final byte[] bytes;

  private SubscriberId(byte[] bytes) {
    this.bytes = bytes;
  }

  public byte[] getBytes() {
    return bytes;
  }

  public @NonNull String serialize() {
    return Base64.encodeUrlSafeWithPadding(bytes);
  }

  public static @NonNull SubscriberId deserialize(@NonNull String serialized) throws IOException {
    byte[] bytes = Base64.decode(serialized);

    return fromBytes(bytes);
  }

  public static SubscriberId fromBytes(byte[] bytes) {
    Preconditions.checkArgument(bytes.length == SIZE);
    return new SubscriberId(bytes);
  }

  public static SubscriberId generate() {
    byte[]       bytes        = new byte[SIZE];
    SecureRandom secureRandom = new SecureRandom();

    secureRandom.nextBytes(bytes);
    return new SubscriberId(bytes);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SubscriberId that = (SubscriberId) o;
    return Arrays.equals(bytes, that.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}
