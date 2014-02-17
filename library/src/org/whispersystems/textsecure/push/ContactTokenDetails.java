package org.whispersystems.textsecure.push;

import com.google.thoughtcrimegson.Gson;

public class ContactTokenDetails {

  private String  token;
  private String  relay;
  private String  number;
  private boolean supportsSms;

  public ContactTokenDetails() {}

  public String getToken() {
    return token;
  }

  public String getRelay() {
    return relay;
  }

  public boolean isSupportsSms() {
    return supportsSms;
  }

  public void setNumber(String number) {
    this.number = number;
  }

  public String getNumber() {
    return number;
  }
}
