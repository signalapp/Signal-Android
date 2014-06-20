package org.thoughtcrime.securesms.sms;

public class IncomingPreKeyBundleMessage extends IncomingKeyExchangeMessage {

  public IncomingPreKeyBundleMessage(IncomingTextMessage base, String newBody) {
    super(base, newBody);
  }

  @Override
  public IncomingPreKeyBundleMessage withMessageBody(String messageBody) {
    return new IncomingPreKeyBundleMessage(this, messageBody);
  }

  @Override
  public boolean isPreKeyBundle() {
    return true;
  }
}
