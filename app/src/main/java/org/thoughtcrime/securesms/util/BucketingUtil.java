package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.Recipient;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

/**
 * Logic to bucket a user for a given feature flag based on their UUID.
 */
public final class BucketingUtil {

  private BucketingUtil() {}

  /**
   * Calculate a user bucket for a given feature flag, uuid, and part per modulus.
   *
   * @param key Feature flag key (e.g., "research.megaphone.1")
   * @param uuid Current user's UUID (see {@link Recipient#getServiceId()})
   * @param modulus Drives the bucketing parts per N (e.g., passing 1,000,000 indicates bucketing into parts per million)
   */
  public static long bucket(@NonNull String key, @NonNull UUID uuid, long modulus) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }

    digest.update(key.getBytes());
    digest.update(".".getBytes());

    ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
    byteBuffer.order(ByteOrder.BIG_ENDIAN);
    byteBuffer.putLong(uuid.getMostSignificantBits());
    byteBuffer.putLong(uuid.getLeastSignificantBits());

    digest.update(byteBuffer.array());

    return new BigInteger(Arrays.copyOfRange(digest.digest(), 0, 8)).mod(BigInteger.valueOf(modulus)).longValue();
  }
}
