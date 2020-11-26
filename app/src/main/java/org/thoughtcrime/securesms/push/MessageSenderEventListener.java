package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.session.libsignal.service.api.SignalServiceMessageSender;
import org.session.libsignal.service.api.push.SignalServiceAddress;

public class MessageSenderEventListener implements SignalServiceMessageSender.EventListener {
  private final Context context;

  public MessageSenderEventListener(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onSecurityEvent(SignalServiceAddress textSecureAddress) {
    SecurityEvent.broadcastSecurityUpdateEvent(context);
  }
}
