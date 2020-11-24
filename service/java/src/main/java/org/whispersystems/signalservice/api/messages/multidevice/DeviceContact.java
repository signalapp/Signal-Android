/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;

public class DeviceContact {

  private final String                                  number;
  private final Optional<String>                        name;
  private final Optional<SignalServiceAttachmentStream> avatar;
  private final Optional<String>                        color;
  private final Optional<VerifiedMessage>               verified;
  private final Optional<byte[]>                        profileKey;
  private final boolean                                 blocked;
  private final Optional<Integer>                       expirationTimer;

  public DeviceContact(String number, Optional<String> name,
                       Optional<SignalServiceAttachmentStream> avatar,
                       Optional<String> color,
                       Optional<VerifiedMessage> verified,
                       Optional<byte[]> profileKey,
                       boolean blocked,
                       Optional<Integer> expirationTimer)
  {
    this.number          = number;
    this.name            = name;
    this.avatar          = avatar;
    this.color           = color;
    this.verified        = verified;
    this.profileKey      = profileKey;
    this.blocked         = blocked;
    this.expirationTimer = expirationTimer;
  }

  public Optional<SignalServiceAttachmentStream> getAvatar() {
    return avatar;
  }

  public Optional<String> getName() {
    return name;
  }

  public String getNumber() {
    return number;
  }

  public Optional<String> getColor() {
    return color;
  }

  public Optional<VerifiedMessage> getVerified() {
    return verified;
  }

  public Optional<byte[]> getProfileKey() {
    return profileKey;
  }

  public boolean isBlocked() {
    return blocked;
  }

  public Optional<Integer> getExpirationTimer() {
    return expirationTimer;
  }
}
