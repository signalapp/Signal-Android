package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

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
