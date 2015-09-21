package org.thoughtcrime.redphone.signaling;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RedPhoneGcmId {

  @JsonProperty
  private String gcmRegistrationId;

  public RedPhoneGcmId() {}

  public RedPhoneGcmId(String gcmRegistrationId) {
    this.gcmRegistrationId = gcmRegistrationId;
  }


}
