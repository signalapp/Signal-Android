package org.whispersystems.signalservice.api.push;

import com.google.protobuf.ByteString;

import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * A wrapper around a UUID that represents an identifier for an account. Today, that is either an {@link ACI} or a {@link PNI}.
 * However, that doesn't mean every {@link ServiceId} is an <em>instance</em> of one of those classes. In reality, we often
 * do not know which we have. And it shouldn't really matter.
 *
 * The only times you truly know, and the only times you should actually care, is during CDS refreshes or specific inbound messages
 * that link them together.
 */
public class ServiceId {

  public static final ServiceId UNKNOWN = ServiceId.from(UuidUtil.UNKNOWN_UUID);

  protected final UUID uuid;

  protected ServiceId(UUID uuid) {
    this.uuid = uuid;
  }

  public static ServiceId from(UUID uuid) {
    return new ServiceId(uuid);
  }

  public static ServiceId parseOrThrow(String raw) {
    return from(UUID.fromString(raw));
  }

  public static ServiceId parseOrThrow(byte[] raw) {
    return from(UuidUtil.parseOrThrow(raw));
  }

  public static @Nullable ServiceId parseOrNull(String raw) {
    UUID uuid = UuidUtil.parseOrNull(raw);
    return uuid != null ? from(uuid) : null;
  }

  public static ServiceId parseOrNull(byte[] raw) {
    UUID uuid = UuidUtil.parseOrNull(raw);
    return uuid != null ? from(uuid) : null;
  }

  public static ServiceId parseOrUnknown(String raw) {
    ServiceId aci = parseOrNull(raw);
    return aci != null ? aci : UNKNOWN;
  }

  public static ServiceId fromByteString(ByteString bytes) {
    return parseOrThrow(bytes.toByteArray());
  }

  public static ServiceId fromByteStringOrNull(ByteString bytes) {
    UUID uuid = UuidUtil.fromByteStringOrNull(bytes);
    return uuid != null ? from(uuid) : null;
  }

  public static ServiceId fromByteStringOrUnknown(ByteString bytes) {
    ServiceId uuid = fromByteStringOrNull(bytes);
    return uuid != null ? uuid : UNKNOWN;
  }

  public UUID uuid() {
    return uuid;
  }

  public boolean isUnknown() {
    return uuid.equals(UNKNOWN.uuid);
  }

  public boolean isValid() {
    return !isUnknown();
  }

  public SignalProtocolAddress toProtocolAddress(int deviceId) {
    return new SignalProtocolAddress(uuid.toString(), deviceId);
  }

  public ByteString toByteString() {
    return UuidUtil.toByteString(uuid);
  }

  public byte[] toByteArray() {
    return UuidUtil.toByteArray(uuid);
  }

  public static List<ServiceId> filterKnown(Collection<ServiceId> serviceIds) {
    return serviceIds.stream().filter(sid -> !sid.equals(UNKNOWN)).collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return uuid.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ServiceId)) return false;
    final ServiceId serviceId = (ServiceId) o;
    return Objects.equals(uuid, serviceId.uuid);
  }

  @Override
  public int hashCode() {
    return uuid.hashCode();
  }
}
