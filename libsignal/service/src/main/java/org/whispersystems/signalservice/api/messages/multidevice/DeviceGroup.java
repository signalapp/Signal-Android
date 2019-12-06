/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;

public class DeviceGroup {

  private final byte[]                                  id;
  private final Optional<String>                        name;
  private final List<SignalServiceAddress>              members;
  private final Optional<SignalServiceAttachmentStream> avatar;
  private final boolean                                 active;
  private final Optional<Integer>                       expirationTimer;
  private final Optional<String>                        color;
  private final boolean                                 blocked;

  public DeviceGroup(byte[] id, Optional<String> name, List<SignalServiceAddress> members,
                     Optional<SignalServiceAttachmentStream> avatar,
                     boolean active, Optional<Integer> expirationTimer,
                     Optional<String> color, boolean blocked)
  {
    this.id              = id;
    this.name            = name;
    this.members         = members;
    this.avatar          = avatar;
    this.active          = active;
    this.expirationTimer = expirationTimer;
    this.color           = color;
    this.blocked         = blocked;
  }

  public Optional<SignalServiceAttachmentStream> getAvatar() {
    return avatar;
  }

  public Optional<String> getName() {
    return name;
  }

  public byte[] getId() {
    return id;
  }

  public List<SignalServiceAddress> getMembers() {
    return members;
  }

  public boolean isActive() {
    return active;
  }

  public Optional<Integer> getExpirationTimer() {
    return expirationTimer;
  }

  public Optional<String> getColor() {
    return color;
  }

  public boolean isBlocked() {
    return blocked;
  }
}
