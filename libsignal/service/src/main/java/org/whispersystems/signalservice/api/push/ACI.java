package org.whispersystems.signalservice.api.push;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An ACI is an "Account Identity". They're just UUIDs, but given multiple different things could be UUIDs, this wrapper exists to give us type safety around
 * this *specific type* of UUID.
 */
public final class ACI extends AccountIdentifier {

  public static final ACI UNKNOWN = ACI.from(UuidUtil.UNKNOWN_UUID);

  public static ACI from(UUID uuid) {
    return new ACI(uuid);
  }

  public static Optional<ACI> parse(String raw) {
    return UuidUtil.parse(raw).transform(ACI::from);
  }

  public static ACI parseOrThrow(String raw) {
    return from(UUID.fromString(raw));
  }

  public static ACI parseOrThrow(byte[] raw) {
    return from(UuidUtil.parseOrThrow(raw));
  }

  public static ACI parseOrNull(String raw) {
    UUID uuid = UuidUtil.parseOrNull(raw);
    return uuid != null ? from(uuid) : null;
  }

  public static ACI parseOrNull(byte[] raw) {
    UUID uuid = UuidUtil.parseOrNull(raw);
    return uuid != null ? from(uuid) : null;
  }

  public static ACI parseOrUnknown(String raw) {
    ACI aci = parseOrNull(raw);
    return aci != null ? aci : UNKNOWN;
  }

  public static ACI fromByteString(ByteString bytes) {
    return parseOrThrow(bytes.toByteArray());
  }

  public static ACI fromByteStringOrNull(ByteString bytes) {
    UUID uuid = UuidUtil.fromByteStringOrNull(bytes);
    return uuid != null ? from(uuid) : null;
  }

  public static ACI fromByteStringOrUnknown(ByteString bytes) {
    ACI uuid = fromByteStringOrNull(bytes);
    return uuid != null ? uuid : UNKNOWN;
  }

  public static List<ACI> filterKnown(Collection<ACI> acis) {
    return acis.stream().filter(aci -> !aci.equals(UNKNOWN)).collect(Collectors.toList());
  }

  private ACI(UUID uuid) {
    super(uuid);
  }

  public ByteString toByteString() {
    return UuidUtil.toByteString(uuid);
  }

  public byte[] toByteArray() {
    return UuidUtil.toByteArray(uuid);
  }

  public boolean isUnknown() {
    return this.equals(UNKNOWN);
  }

  @Override
  public int hashCode() {
    return uuid.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof ACI) {
      return uuid.equals(((ACI) other).uuid);
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return uuid.toString();
  }
}
