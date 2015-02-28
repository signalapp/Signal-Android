package org.whispersystems.textsecure.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceCode {

  @JsonProperty
  private String verificationCode;

  public String getVerificationCode() {
    return verificationCode;
  }
}
