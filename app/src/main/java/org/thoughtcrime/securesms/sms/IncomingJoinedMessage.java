package org.thoughtcrime.securesms.sms;

import org.session.libsession.messaging.threads.Address;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.messages.SignalServiceGroup;

public class IncomingJoinedMessage extends IncomingTextMessage {

  public IncomingJoinedMessage(Address sender) {
    super(sender, 1, System.currentTimeMillis(), null, Optional.<SignalServiceGroup>absent(), 0, false);
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
