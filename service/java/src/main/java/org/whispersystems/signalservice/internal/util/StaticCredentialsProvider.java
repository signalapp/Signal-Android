/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.util;

import org.whispersystems.signalservice.api.util.CredentialsProvider;

public class StaticCredentialsProvider implements CredentialsProvider {

  private final String user;
  private final String password;
  private final String signalingKey;

  public StaticCredentialsProvider(String user, String password, String signalingKey) {
    this.user         = user;
    this.password     = password;
    this.signalingKey = signalingKey;
  }

  @Override
  public String getUser() {
    return user;
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
