package org.whispersystems.signalservice.api.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import okio.ByteString;

public final class UuidUtil {

  public static final UUID UNKNOWN_UUID = new UUID(0, 0);
  public static final String UNKNOWN_UUID_STRING = UNKNOWN_UUID.toString();

  private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);

  private UuidUtil() { }

  public static Optional<UUID> parse(String uuid) {
    return Optional.ofNullable(parseOrNull(uuid));
  }

  public static UUID parseOrNull(@Nullable String uuid) {
    return isUuid(uuid) ? parseOrThrow(uuid) : null;
  }

  public static UUID parseOrUnknown(String uuid) {
    return uuid == null || uuid.isEmpty() ? UNKNOWN_UUID : parseOrThrow(uuid);
  }

  public static UUID parseOrThrow(String uuid) {
    return UUID.fromString(uuid);
  }

  public static UUID parseOrThrow(byte[] bytes) {
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
    long       high       = byteBuffer.getLong();
    long       low        = byteBuffer.getLong();

    return new UUID(high, low);
  }

  public static boolean isUuid(String uuid) {
    return uuid != null && UUID_PATTERN.matcher(uuid).matches();
  }

  public static byte[] toByteArray(@Nonnull UUID uuid) {
    ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
    buffer.putLong(uuid.getMostSignificantBits());
    buffer.putLong(uuid.getLeastSignificantBits());

    return buffer.array();
  }

  public static ByteString toByteString(@Nonnull UUID uuid) {
    return ByteString.of(toByteArray(uuid));
  }

  public static UUID fromByteString(ByteString bytes) {
    return parseOrThrow(bytes.toByteArray());
  }

  public static @Nullable UUID fromByteStringOrNull(@Nullable ByteString bytes) {
    if (bytes == null) {
      return null;
    }

    return parseOrNull(bytes.toByteArray());
  }

  public static UUID fromByteStringOrUnknown(ByteString bytes) {
    UUID uuid = fromByteStringOrNull(bytes);
    return uuid != null ? uuid : UNKNOWN_UUID;
  }

  public static UUID parseOrNull(byte[] byteArray) {
    return byteArray != null && byteArray.length == 16 ? parseOrThrow(byteArray) : null;
  }

  public static List<UUID> fromByteStrings(Collection<ByteString> byteStringCollection) {
    ArrayList<UUID> result = new ArrayList<>(byteStringCollection.size());

    for (ByteString byteString : byteStringCollection) {
      result.add(fromByteString(byteString));
    }

    return result;
  }

  /**
   * Keep only UUIDs that are not the {@link #UNKNOWN_UUID}.
   */
  public static List<UUID> filterKnown(Collection<UUID> uuids) {
    ArrayList<UUID> result = new ArrayList<>(uuids.size());

    for (UUID uuid : uuids) {
      if (!UNKNOWN_UUID.equals(uuid)) {
        result.add(uuid);
      }
    }

    return result;
  }
}
