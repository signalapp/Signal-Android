/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.push;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.libsignal.util.guava.Preconditions;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Objects;

/**
 * A class representing a message destination or origin.
 */
public class SignalServiceAddress {

  public static final int DEFAULT_DEVICE_ID = 1;

  private final ServiceId        serviceId;
  private final Optional<String> e164;

  /**
   * Construct a PushAddress.
   *
   * @param serviceId The UUID of the user, if available.
   * @param e164 The phone number of the user, if available.
   */
  public SignalServiceAddress(ServiceId serviceId, Optional<String> e164) {
    this.serviceId = Preconditions.checkNotNull(serviceId);
    this.e164      = e164;
  }

  public SignalServiceAddress(ServiceId serviceId) {
    this.serviceId = Preconditions.checkNotNull(serviceId);
    this.e164      = Optional.absent();
  }

  /**
   * Convenience constructor that will consider a UUID/E164 string absent if it is null or empty.
   */
  public SignalServiceAddress(ServiceId serviceId, String e164) {
    this(serviceId, OptionalUtil.absentIfEmpty(e164));
  }

  public Optional<String> getNumber() {
    return e164;
  }

  public ServiceId getServiceId() {
    return serviceId;
  }

  public boolean hasValidServiceId() {
    return !serviceId.isUnknown();
  }

  public String getIdentifier() {
    return serviceId.toString();
  }

  public boolean matches(SignalServiceAddress other) {
    return this.serviceId.equals(other.serviceId);
  }

  public static boolean isValidAddress(String rawUuid) {
    return isValidAddress(rawUuid, null);
  }

  public static boolean isValidAddress(String rawUuid, String e164) {
    return UuidUtil.parseOrNull(rawUuid) != null;
  }

  public static Optional<SignalServiceAddress> fromRaw(String rawUuid, String e164) {
    if (isValidAddress(rawUuid, e164)) {
      return Optional.of(new SignalServiceAddress(ServiceId.parseOrThrow(rawUuid), e164));
    } else {
      return Optional.absent();
    }
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SignalServiceAddress that = (SignalServiceAddress) o;
    return serviceId.equals(that.serviceId) && e164.equals(that.e164);
  }

  @Override public int hashCode() {
    return Objects.hash(serviceId, e164);
  }
}
