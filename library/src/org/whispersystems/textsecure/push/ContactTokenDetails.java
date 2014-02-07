package org.whispersystems.textsecure.push;

import com.google.thoughtcrimegson.Gson;

public class ContactTokenDetails extends ContactDetails {

  private String  token;

  public ContactTokenDetails() { super(); }

  public ContactTokenDetails(String token) {
    super();
    this.token = token;
  }

  public ContactTokenDetails(String token, String relay) {
    super(relay);
    this.token = token;
  }

  public String getToken() {
    return token;
  }

}
