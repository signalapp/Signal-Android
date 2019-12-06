/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.util;

import org.whispersystems.signalservice.api.util.CredentialsProvider;

import java.util.UUID;

public class StaticCredentialsProvider implements CredentialsProvider {

  private final UUID   uuid;
  private final String e164;
  private final String password;
  private final String signalingKey;

  public StaticCredentialsProvider(UUID uuid, String e164, String password, String signalingKey) {
    this.uuid         = uuid;
    this.e164         = e164;
    this.password     = password;
    this.signalingKey = signalingKey;
  }

  @Override
  public UUID getUuid() {
    return uuid;
  }

  @Override
  public String getE164() {
    return e164;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getSignalingKey() {
    return signalingKey;
  }
}
