package org.thoughtcrime.securesms.sms;

public class OutgoingEndSessionMessage extends OutgoingTextMessage {

  public OutgoingEndSessionMessage(OutgoingTextMessage base) {
    this(base, base.getMessageBody());
  }

  public OutgoingEndSessionMessage(OutgoingTextMessage message, String body) {
    super(message, body);
  }

  @Override
  public boolean isEndSession() {
    return true;
  }

  @Override
  public OutgoingTextMessage withBody(String body) {
    return new OutgoingEndSessionMessage(this, body);
  }
}
