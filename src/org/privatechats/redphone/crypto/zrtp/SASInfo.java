package org.thoughtcrime.redphone.crypto.zrtp;

public class SASInfo {

  private final String sasText;
  private final boolean verified;

  public SASInfo(String sasText, boolean verified) {
    this.sasText  = sasText;
    this.verified = verified;
  }

  public String getSasText() {
    return sasText;
  }

  public boolean isVerified() {
    return verified;
  }
}
