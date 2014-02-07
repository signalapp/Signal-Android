package org.whispersystems.textsecure.push;

import com.google.thoughtcrimegson.Gson;

public abstract class ContactDetails {

  private String  relay;
  private boolean supportsSms;

  public ContactDetails() {}

  public ContactDetails(String relay) {
    this.relay = relay;
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
