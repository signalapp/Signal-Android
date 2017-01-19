package org.thoughtcrime.securesms.sms;


public class OutgoingPrekeyBundleMessage extends OutgoingTextMessage {

  public OutgoingPrekeyBundleMessage(OutgoingTextMessage message, String body) {
    super(message, body);
  }

  @Override
  public boolean isPreKeyBundle() {
    return true;
  }

  @Override
  public OutgoingTextMessage withBody(String body) {
    return new OutgoingPrekeyBundleMessage(this, body);
  }
}
