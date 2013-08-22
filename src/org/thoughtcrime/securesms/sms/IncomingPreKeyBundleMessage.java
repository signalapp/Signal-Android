package org.thoughtcrime.securesms.sms;

import org.whispersystems.textsecure.push.IncomingPushMessage;

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
