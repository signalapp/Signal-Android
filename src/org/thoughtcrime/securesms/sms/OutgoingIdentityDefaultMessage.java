package org.thoughtcrime.securesms.sms;


import org.thoughtcrime.securesms.recipients.Recipients;

public class OutgoingIdentityDefaultMessage extends OutgoingTextMessage {

  public OutgoingIdentityDefaultMessage(Recipients recipients) {
    super(recipients, "", -1);
  }

  @Override
  public boolean isIdentityDefault() {
    return true;
  }

  public OutgoingTextMessage withBody(String body) {
    return new OutgoingIdentityDefaultMessage(getRecipients());
  }
}
