package org.thoughtcrime.securesms.sms;


public class IncomingIdentityVerifiedMessage extends IncomingTextMessage {

  public IncomingIdentityVerifiedMessage(IncomingTextMessage base) {
    super(base, "");
  }

  @Override
  public boolean isIdentityVerified() {
    return true;
  }

}
