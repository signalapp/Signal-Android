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

  private final ACI              aci;
  private final Optional<String> e164;

  /**
   * Construct a PushAddress.
   *
   * @param aci The UUID of the user, if available.
   * @param e164 The phone number of the user, if available.
   */
  public SignalServiceAddress(ACI aci, Optional<String> e164) {
    this.aci  = Preconditions.checkNotNull(aci);
    this.e164 = e164;
  }

  public SignalServiceAddress(ACI aci) {
    this.aci  = Preconditions.checkNotNull(aci);
    this.e164 = Optional.absent();
  }

  /**
   * Convenience constructor that will consider a UUID/E164 string absent if it is null or empty.
   */
  public SignalServiceAddress(ACI aci, String e164) {
    this(aci, OptionalUtil.absentIfEmpty(e164));
  }

  public Optional<String> getNumber() {
    return e164;
  }

  public ACI getAci() {
    return aci;
  }

  public boolean hasValidAci() {
    return !aci.uuid().equals(UuidUtil.UNKNOWN_UUID);
  }

  public String getIdentifier() {
    return aci.toString();
  }

  public boolean matches(SignalServiceAddress other) {
    return this.aci.equals(other.aci);
  }

  public static boolean isValidAddress(String rawUuid, String e164) {
    return UuidUtil.parseOrNull(rawUuid) != null;
  }

  public static Optional<SignalServiceAddress> fromRaw(String rawUuid, String e164) {
    if (isValidAddress(rawUuid, e164)) {
      return Optional.of(new SignalServiceAddress(ACI.parseOrThrow(rawUuid), e164));
    } else {
      return Optional.absent();
    }
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SignalServiceAddress that = (SignalServiceAddress) o;
    return aci.equals(that.aci) && e164.equals(that.e164);
  }

  @Override public int hashCode() {
    return Objects.hash(aci, e164);
  }
}
