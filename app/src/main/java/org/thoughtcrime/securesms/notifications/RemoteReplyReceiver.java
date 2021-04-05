/*
 * Copyright (C) 2016 Open Whisper Systems
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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.core.app.RemoteInput;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Get the response text from the Wearable Device and sends an message as a reply
 */
public class RemoteReplyReceiver extends BroadcastReceiver {

  public static final String TAG             = RemoteReplyReceiver.class.getSimpleName();
  public static final String REPLY_ACTION    = "org.thoughtcrime.securesms.notifications.WEAR_REPLY";
  public static final String RECIPIENT_EXTRA = "recipient_extra";
  public static final String REPLY_METHOD    = "reply_method";

  @SuppressLint("StaticFieldLeak")
  @Override
  public void onReceive(final Context context, Intent intent) {
    if (!REPLY_ACTION.equals(intent.getAction())) return;

    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);

    if (remoteInput == null) return;

    final RecipientId  recipientId  = intent.getParcelableExtra(RECIPIENT_EXTRA);
    final ReplyMethod  replyMethod  = (ReplyMethod) intent.getSerializableExtra(REPLY_METHOD);
    final CharSequence responseText = remoteInput.getCharSequence(DefaultMessageNotifier.EXTRA_REMOTE_REPLY);

    if (recipientId == null) throw new AssertionError("No recipientId specified");
    if (replyMethod == null) throw new AssertionError("No reply method specified");

    if (responseText != null) {
      SignalExecutors.BOUNDED.execute(() -> {
        long threadId;

        Recipient recipient      = Recipient.resolved(recipientId);
        int       subscriptionId = recipient.getDefaultSubscriptionId().or(-1);
        long      expiresIn      = recipient.getExpireMessages() * 1000L;

        switch (replyMethod) {
          case GroupMessage: {
            OutgoingMediaMessage reply = new OutgoingMediaMessage(recipient,
                                                                  responseText.toString(),
                                                                  new LinkedList<>(),
                                                                  System.currentTimeMillis(),
                                                                  subscriptionId,
                                                                  expiresIn,
                                                                  false,
                                                                  0,
                                                                  null,
                                                                  Collections.emptyList(),
                                                                  Collections.emptyList(),
                                                                  Collections.emptyList(),
                                                                  Collections.emptyList(),
                                                                  Collections.emptyList());
            threadId = MessageSender.send(context, reply, -1, false, null);
            break;
          }
          case SecureMessage: {
            OutgoingEncryptedMessage reply = new OutgoingEncryptedMessage(recipient, responseText.toString(), expiresIn);
            threadId = MessageSender.send(context, reply, -1, false, null);
            break;
          }
          case UnsecuredSmsMessage: {
            OutgoingTextMessage reply = new OutgoingTextMessage(recipient, responseText.toString(), expiresIn, subscriptionId);
            threadId = MessageSender.send(context, reply, -1, true, null);
            break;
          }
          default:
            throw new AssertionError("Unknown Reply method");
        }

        List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(threadId, true);

        ApplicationDependencies.getMessageNotifier().updateNotification(context);
        MarkReadReceiver.process(context, messageIds);
      });
    }
  }
}
