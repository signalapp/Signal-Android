/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

public class AccountAttributes {

  @JsonProperty
  private String  signalingKey;

  @JsonProperty
  private int     registrationId;

  @JsonProperty
  private boolean voice;

  @JsonProperty
  private boolean video;

  @JsonProperty
  private boolean fetchesMessages;

  @JsonProperty
  private String pin;

  @JsonProperty
  private String registrationLock;

  @JsonProperty
  private byte[] unidentifiedAccessKey;

  @JsonProperty
  private boolean unrestrictedUnidentifiedAccess;

  @JsonProperty
  private SignalServiceProfile.Capabilities capabilities;

  public AccountAttributes(String signalingKey,
                           int registrationId,
                           boolean fetchesMessages,
                           String pin,
                           String registrationLock,
                           byte[] unidentifiedAccessKey,
                           boolean unrestrictedUnidentifiedAccess,
                           SignalServiceProfile.Capabilities capabilities)
  {
    this.signalingKey                   = signalingKey;
    this.registrationId                 = registrationId;
    this.voice                          = true;
    this.video                          = true;
    this.fetchesMessages                = fetchesMessages;
    this.pin                            = pin;
    this.registrationLock               = registrationLock;
    this.unidentifiedAccessKey          = unidentifiedAccessKey;
    this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
    this.capabilities                   = capabilities;
  }

  public AccountAttributes() {}

  public String getSignalingKey() {
    return signalingKey;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public boolean isVoice() {
    return voice;
  }

  public boolean isVideo() {
    return video;
  }

  public boolean isFetchesMessages() {
    return fetchesMessages;
  }

  public String getPin() {
    return pin;
  }

  public String getRegistrationLock() {
    return registrationLock;
  }

  public byte[] getUnidentifiedAccessKey() {
    return unidentifiedAccessKey;
  }

  public boolean isUnrestrictedUnidentifiedAccess() {
    return unrestrictedUnidentifiedAccess;
  }

  public SignalServiceProfile.Capabilities getCapabilities() {
    return capabilities;
  }
}
