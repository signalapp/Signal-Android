package org.thoughtcrime.securesms;

public class TransportOption {
  public int    drawable;
  public String text;
  public String key;
  public String composeHint;

  public TransportOption(String key, int drawable, String text, String composeHint) {
    this.key         = key;
    this.drawable    = drawable;
    this.text        = text;
    this.composeHint = composeHint;
  }

  public boolean isForcedPlaintext() {
    return key.equals("insecure_sms");
  }

  public boolean isForcedSms() {
    return key.equals("insecure_sms") || key.equals("secure_sms");
  }
}

