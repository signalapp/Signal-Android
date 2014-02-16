package org.thoughtcrime.securesms.sms;

public class IncomingIdentityUpdateMessage extends IncomingKeyExchangeMessage {

  public IncomingIdentityUpdateMessage(IncomingTextMessage base, String newBody) {
    super(base, newBody);
  }

  @Override
  public IncomingIdentityUpdateMessage withMessageBody(String messageBody) {
    return new IncomingIdentityUpdateMessage(this, messageBody);
  }

  @Override
  public boolean isIdentityUpdate() {
    return true;
  }
}
