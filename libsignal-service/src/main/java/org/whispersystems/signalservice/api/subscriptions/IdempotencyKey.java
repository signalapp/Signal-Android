package org.whispersystems.signalservice.api.subscriptions;


import org.signal.core.util.Base64;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Key representing an atomic operation against the Subscriptions API
 */
public final class IdempotencyKey {

  private static final int SIZE = 16;

  private final byte[] bytes;

  private IdempotencyKey(byte[] bytes) {
    this.bytes = bytes;
  }

  public byte[] getBytes() {
    return bytes;
  }

  public String serialize() {
    return Base64.encodeUrlSafeWithPadding(bytes);
  }

  public static IdempotencyKey fromBytes(byte[] bytes) {
    Preconditions.checkArgument(bytes.length == SIZE);
    return new IdempotencyKey(bytes);
  }

  public static IdempotencyKey generate() {
    byte[]       bytes        = new byte[SIZE];
    SecureRandom secureRandom = new SecureRandom();

    secureRandom.nextBytes(bytes);
    return new IdempotencyKey(bytes);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final IdempotencyKey that = (IdempotencyKey) o;
    return Arrays.equals(bytes, that.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}
