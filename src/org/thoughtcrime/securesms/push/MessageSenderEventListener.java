package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.loki.protocol.FriendRequestProtocol;
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

  @Override
  public void onFriendRequestSending(long messageID, long threadID) {
    FriendRequestProtocol.setFriendRequestStatusToSendingIfNeeded(context, messageID, threadID);
  }

  @Override
  public void onFriendRequestSent(long messageID, long threadID) {
    FriendRequestProtocol.setFriendRequestStatusToSentIfNeeded(context, messageID, threadID);
  }

  @Override
  public void onFriendRequestSendingFailed(long messageID, long threadID) {
    FriendRequestProtocol.setFriendRequestStatusToFailedIfNeeded(context, messageID, threadID);
  }
}
