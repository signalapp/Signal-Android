package org.thoughtcrime.securesms.sms;

import org.session.libsession.messaging.threads.recipients.Recipient;

public class OutgoingEncryptedMessage extends OutgoingTextMessage {

  public OutgoingEncryptedMessage(Recipient recipient, String body, long expiresIn) {
    super(recipient, body, expiresIn, -1);
  }

  @Override
  public boolean isSecureMessage() {
    return true;
  }
}
