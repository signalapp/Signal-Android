package org.thoughtcrime.securesms.sms;


import org.thoughtcrime.securesms.recipients.Recipients;

public class OutgoingIdentityVerifiedMessage extends OutgoingTextMessage {

  public OutgoingIdentityVerifiedMessage(Recipients recipients) {
    this(recipients, "");
  }

  private OutgoingIdentityVerifiedMessage(Recipients recipients, String body) {
    super(recipients, body, -1);
  }

  @Override
  public boolean isIdentityVerified() {
    return true;
  }

  @Override
  public OutgoingTextMessage withBody(String body) {
    return new OutgoingIdentityVerifiedMessage(getRecipients(), body);
  }
}
