package org.privatechats.redphone.signaling;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RedPhoneAccountAttributes {

  @JsonProperty
  private String signalingKey;

  @JsonProperty
  private String gcmRegistrationId;

  @JsonProperty
  private boolean textsecure;

  public RedPhoneAccountAttributes() {}

  public RedPhoneAccountAttributes(String signalingKey, String gcmRegistrationId) {
    this.signalingKey      = signalingKey;
    this.gcmRegistrationId = gcmRegistrationId;
    this.textsecure        = true;
  }

}
