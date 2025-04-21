package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON POJO that represents the returned ACI from a call to
 * /v1/account/username/[username]
 */
public class GetAciByUsernameResponse {
  @JsonProperty
  private String uuid;

  public GetAciByUsernameResponse() {}

  public String getUuid() {
    return uuid;
  }
}
