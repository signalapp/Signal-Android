package org.thoughtcrime.securesms;

public class TransportOption {
  public final int    drawableButtonIcon;
  public final int    drawableSendButtonIcon;
  public final String text;
  public final String key;
  public final String composeHint;

  public TransportOption(String key, int drawableButtonIcon, int drawableSendButtonIcon, String text, String composeHint) {
    this.key                    = key;
    this.drawableButtonIcon     = drawableButtonIcon;
    this.drawableSendButtonIcon = drawableSendButtonIcon;
    this.text                   = text;
    this.composeHint            = composeHint;
  }

  public boolean isForcedPlaintext() {
    return key.equals("insecure_sms");
  }

  public boolean isForcedSms() {
    return key.equals("insecure_sms") || key.equals("secure_sms");
  }
}

