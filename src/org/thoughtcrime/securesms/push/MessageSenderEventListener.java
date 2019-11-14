package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.loki.FriendRequestHandler;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class MessageSenderEventListener implements SignalServiceMessageSender.EventListener {

  private static final String TAG = MessageSenderEventListener.class.getSimpleName();

  private final Context context;

  public MessageSenderEventListener(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onSecurityEvent(SignalServiceAddress textSecureAddress) {
    SecurityEvent.broadcastSecurityUpdateEvent(context);
  }

  @Override
  public void onSyncEvent(long messageID, long timestamp, byte[] message, int ttl) {
    if (messageID >= 0 && timestamp > 0 && message != null && ttl > 0) {
      MessageSender.sendSyncMessageToOurDevices(context, messageID, timestamp, message, ttl);
    }
  }

  @Override public void onFriendRequestSending(long messageID, long threadID) {
    FriendRequestHandler.updateFriendRequestState(context, FriendRequestHandler.ActionType.Sending, messageID, threadID);
  }

  @Override public void onFriendRequestSent(long messageID, long threadID) {
    FriendRequestHandler.updateFriendRequestState(context, FriendRequestHandler.ActionType.Sent, messageID, threadID);
  }

  @Override public void onFriendRequestSendingFail(long messageID, long threadID) {
    FriendRequestHandler.updateFriendRequestState(context, FriendRequestHandler.ActionType.Failed, messageID, threadID);
  }
}
