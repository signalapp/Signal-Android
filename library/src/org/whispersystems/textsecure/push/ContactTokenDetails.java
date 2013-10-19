package org.whispersystems.textsecure.push;

import com.google.thoughtcrimegson.Gson;

public class ContactTokenDetails {

  private String  token;
  private String  relay;
  private boolean supportsSms;

  public ContactTokenDetails() {}

  public ContactTokenDetails(String token) {
    this.token = token;
  }

  public ContactTokenDetails(String token, String relay) {
    this.token = token;
    this.relay = relay;
  }

  public String getToken() {
    return token;
  }

  public String getRelay() {
    return relay;
  }

  public void setRelay(String relay) {
    this.relay = relay;
  }

  public boolean isSupportsSms() {
    return supportsSms;
  }

  public String toString() {
    return new Gson().toJson(this);
  }
}
