package org.thoughtcrime.securesms.notifications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.RemoteInput;
import android.util.Log;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.storage.Session;

import ws.com.google.android.mms.MmsException;

public class QuickRespondReceiver extends BroadcastReceiver {

  public static final String QUICK_RESPOND_ACTION = "org.thoughtcrime.securesms.notifications.QUICK_RESPOND";

  @Override
  public void onReceive(final Context context, Intent intent) {
    if (!intent.getAction().equals(QUICK_RESPOND_ACTION)) {
      return;
    }

    final long[] threadIds = intent.getLongArrayExtra("thread_ids");
    final MasterSecret masterSecret = intent.getParcelableExtra("master_secret");
    final String messageText = getMessageText(intent);

    if (threadIds != null && masterSecret != null && messageText != null) {
      if (threadIds.length != 1) {
        return;
      }

      final long threadId = threadIds[0];

      ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(
          MessageNotifier.NOTIFICATION_ID);

      new SendMessageTask(threadId, context, masterSecret, messageText).execute();
    }
  }

  private String getMessageText(Intent intent) {
    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
    if (remoteInput != null) {
      CharSequence quickReply = remoteInput.getCharSequence("quick_reply");
      if (quickReply != null) {
        return quickReply.toString();
      }
    }
    return null;
  }

  private static class SendMessageTask extends AsyncTask<Void, Void, Void> {
    private final long threadId;
    private final Context context;
    private final MasterSecret masterSecret;
    private final String messageText;
    private Recipients recipients;
    private boolean isEncryptedConversation;

    public SendMessageTask(long threadId, Context context, MasterSecret masterSecret,
                           String messageText) {
      this.threadId = threadId;
      this.context = context;
      this.masterSecret = masterSecret;
      this.messageText = messageText;
    }

    @Override
    protected Void doInBackground(Void... params) {
      Log.w("QuickRespondReceiver", "Answering: " + threadId + " with text " + messageText);

      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
      recipients = threadDatabase.getRecipientsForThreadId(threadId);

      if (recipients == null) return null;

      boolean isPushDestination = DirectoryHelper.isPushDestination(context, recipients);
      boolean isSecureDestination = recipients.isSingleRecipient() && !recipients.isGroupRecipient()
          && Session.hasSession(context, masterSecret, recipients.getPrimaryRecipient());


      if (isPushDestination || isSecureDestination) {
        this.isEncryptedConversation = true;
      } else {
        this.isEncryptedConversation = false;
      }

      try {
        if (!recipients.isSingleRecipient() || recipients.isGroupRecipient() ||
            recipients.isEmailRecipient()) {
          sendMediaMessage(false, false);
        } else {
          sendTextMessage(false, false);
        }

        threadDatabase.setRead(threadId);
      } catch (InvalidMessageException e) {
        e.printStackTrace();
      } catch (MmsException e) {
        e.printStackTrace();
      }

      MessageNotifier.updateNotification(context, masterSecret);
      return null;
    }


    private void sendMediaMessage(boolean forcePlaintext, boolean forceSms)
        throws InvalidMessageException, MmsException {

      SlideDeck slideDeck = new SlideDeck();

      OutgoingMediaMessage outgoingMessage = new OutgoingMediaMessage(context, recipients, slideDeck,
          messageText, ThreadDatabase.DistributionTypes.DEFAULT);

      if (isEncryptedConversation && !forcePlaintext) {
        outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessage);
      }

      MessageSender.send(context, masterSecret, outgoingMessage, threadId, forceSms);
    }

    private void sendTextMessage(boolean forcePlaintext, boolean forceSms)
        throws InvalidMessageException {
      OutgoingTextMessage message;

      if (isEncryptedConversation && !forcePlaintext) {
        message = new OutgoingEncryptedMessage(recipients, messageText);
      } else {
        message = new OutgoingTextMessage(recipients, messageText);
      }

      MessageSender.send(context, masterSecret, message, threadId, forceSms);
    }

  }
}
