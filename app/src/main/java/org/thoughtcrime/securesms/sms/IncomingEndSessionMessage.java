package org.thoughtcrime.securesms.sms;

public class IncomingEndSessionMessage extends IncomingTextMessage {

  public IncomingEndSessionMessage(IncomingTextMessage base) {
    this(base, base.getMessageBody());
  }

  public IncomingEndSessionMessage(IncomingTextMessage base, String newBody) {
    super(base, newBody);
  }

  @Override
  public boolean isEndSession() {
    return true;
  }
}
