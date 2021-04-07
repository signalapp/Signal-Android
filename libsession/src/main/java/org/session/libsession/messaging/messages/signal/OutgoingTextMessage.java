package org.session.libsession.messaging.messages.signal;

import org.session.libsession.messaging.messages.visible.VisibleMessage;
import org.session.libsession.messaging.threads.recipients.Recipient;

public class OutgoingTextMessage {

  private final Recipient recipient;
  private final String    message;
  private final int       subscriptionId;
  private final long      expiresIn;
  private final long      sentTimestampMillis;

  public OutgoingTextMessage(Recipient recipient, String message, long expiresIn, int subscriptionId, long sentTimestampMillis) {
    this.recipient      = recipient;
    this.message        = message;
    this.expiresIn      = expiresIn;
    this.subscriptionId = subscriptionId;
    this.sentTimestampMillis = sentTimestampMillis;
  }

  public static OutgoingTextMessage from(VisibleMessage message, Recipient recipient) {
    return new OutgoingTextMessage(recipient, message.getText(), recipient.getExpireMessages() * 1000, -1, message.getSentTimestamp());
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public String getMessageBody() {
    return message;
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public long getSentTimestampMillis() {
    return sentTimestampMillis;
  }

  public boolean isSecureMessage() {
    return true;
  }
}
