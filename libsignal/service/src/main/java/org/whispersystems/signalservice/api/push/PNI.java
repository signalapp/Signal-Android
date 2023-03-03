package org.whispersystems.signalservice.api.push;

import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.UUID;

/**
 * A PNI is a "Phone Number Identity". They're just UUIDs, but given multiple different things could be UUIDs, this wrapper exists to give us type safety around
 * this *specific type* of UUID.
 */
public final class PNI extends ServiceId {

  public static PNI from(UUID uuid) {
    return new PNI(uuid);
  }

  public static PNI parseOrNull(String raw) {
    UUID uuid = UuidUtil.parseOrNull(raw);
    return uuid != null ? from(uuid) : null;
  }

  public static PNI parseOrThrow(String raw) {
    return from(UUID.fromString(raw));
  }

  public static PNI parseOrThrow(byte[] raw) {
    return from(UuidUtil.parseOrThrow(raw));
  }

  public static PNI parseOrNull(byte[] raw) {
    UUID uuid = UuidUtil.parseOrNull(raw);
    return uuid != null ? from(uuid) : null;
  }

  private PNI(UUID uuid) {
    super(uuid);
  }
}
