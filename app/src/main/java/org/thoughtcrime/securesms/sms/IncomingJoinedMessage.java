package org.thoughtcrime.securesms.sms;

import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Optional;


public class IncomingJoinedMessage extends IncomingTextMessage {

  public IncomingJoinedMessage(RecipientId sender) {
    super(sender, 1, System.currentTimeMillis(), -1, System.currentTimeMillis(), null, Optional.empty(), 0, false, null);
  }

  @Override
  public boolean isJoined() {
    return true;
  }

  @Override
  public boolean isSecureMessage() {
    return true;
  }

}
