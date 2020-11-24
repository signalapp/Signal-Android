package org.thoughtcrime.securesms.sms;

import org.thoughtcrime.securesms.recipients.Recipient;

public class OutgoingKeyExchangeMessage extends OutgoingTextMessage {

  public OutgoingKeyExchangeMessage(Recipient recipient, String message) {
    super(recipient, message, -1);
  }

  private OutgoingKeyExchangeMessage(OutgoingKeyExchangeMessage base, String body) {
    super(base, body);
  }

  @Override
  public boolean isKeyExchange() {
    return true;
  }

  @Override
  public OutgoingTextMessage withBody(String body) {
    return new OutgoingKeyExchangeMessage(this, body);
  }
}
