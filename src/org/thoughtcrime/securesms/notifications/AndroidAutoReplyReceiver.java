/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms.notifications;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.RemoteInput;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase.RecipientsPreferences;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

/**
 * Get the response text from the Wearable Device and sends an message as a reply
 */
public class AndroidAutoReplyReceiver extends MasterSecretBroadcastReceiver {

  public static final String TAG                 = AndroidAutoReplyReceiver.class.getSimpleName();
  public static final String REPLY_ACTION        = "org.thoughtcrime.securesms.notifications.ANDROID_AUTO_REPLY";
  public static final String RECIPIENT_IDS_EXTRA = "car_recipient_ids";
  public static final String VOICE_REPLY_KEY     = "car_voice_reply_key";
  public static final String THREAD_ID_EXTRA     = "car_reply_thread_id";

  @Override
  protected void onReceive(final Context context, Intent intent,
                           final @Nullable MasterSecret masterSecret)
  {
    if (!REPLY_ACTION.equals(intent.getAction())) return;

    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);

    if (remoteInput == null) return;

    final long[]       recipientIds = intent.getLongArrayExtra(RECIPIENT_IDS_EXTRA);
    final long threadId = intent.getLongExtra(THREAD_ID_EXTRA, -1);
    final CharSequence responseText = getMessageText(intent);
    final Recipients   recipients   = RecipientFactory.getRecipientsForIds(context, recipientIds, false);

    if (responseText != null) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {

          long replyThreadId;

          Optional<RecipientsPreferences> preferences = DatabaseFactory.getRecipientPreferenceDatabase(context).getRecipientsPreferences(recipientIds);
          int  subscriptionId = preferences.isPresent() ? preferences.get().getDefaultSubscriptionId().or(-1) : -1;
          long expiresIn      = preferences.isPresent() ? preferences.get().getExpireMessages() * 1000 : 0;

          if (recipients.isGroupRecipient()) {
            Log.i("AndroidAutoReplyReceiver", "GroupRecipient, Sending media message");
            OutgoingMediaMessage reply = new OutgoingMediaMessage(recipients, responseText.toString(), new LinkedList<Attachment>(), System.currentTimeMillis(), subscriptionId, expiresIn, 0);
            replyThreadId = MessageSender.send(context, masterSecret, reply, threadId, false);
          } else {
              Log.i("AndroidAutoReplyReceiver", "Sending regular message ");
            OutgoingTextMessage reply = new OutgoingTextMessage(recipients, responseText.toString(), expiresIn, subscriptionId);
            replyThreadId = MessageSender.send(context, masterSecret, reply, threadId, false);
          }
          List<SyncMessageId> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(replyThreadId);
          MessageNotifier.updateNotification(context, masterSecret);

          ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
                  .cancel(MessageNotifier.NOTIFICATION_ID);

          return null;
        }
      }.execute();
    }
  }

  private CharSequence getMessageText(Intent intent) {
        Bundle remoteInput =
            RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(VOICE_REPLY_KEY);
        }
        return null;
    }

}
