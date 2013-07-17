/**
 * Copyright (C) 2013 Open Whisper Systems
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
package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.SendReceiveService.ToastHandler;
import org.thoughtcrime.securesms.transport.MmsTransport;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.transport.UniversalTransport;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.SendReq;

public class MmsSender {

  private final Context      context;
  private final ToastHandler toastHandler;

  public MmsSender(Context context, ToastHandler toastHandler) {
    this.context      = context;
    this.toastHandler = toastHandler;
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    Log.w("MmsSender", "Got intent action: " + intent.getAction());
    if (intent.getAction().equals(SendReceiveService.SEND_MMS_ACTION)) {
      handleSendMms(masterSecret, intent);
    }
  }

  private void handleSendMms(MasterSecret masterSecret, Intent intent) {
    long               messageId = intent.getLongExtra("message_id", -1);
    MmsDatabase        database  = DatabaseFactory.getMmsDatabase(context);
    ThreadDatabase     threads   = DatabaseFactory.getThreadDatabase(context);
    UniversalTransport transport = new UniversalTransport(context, masterSecret);

    try {
      SendReq[] messages = database.getOutgoingMessages(masterSecret, messageId);

      for (SendReq message : messages) {
        try {
          Log.w("MmsSender", "Passing to MMS transport: " + message.getDatabaseMessageId());
          database.markAsSending(message.getDatabaseMessageId());
          Pair<byte[], Integer> result = transport.deliver(message);
          database.markAsSent(message.getDatabaseMessageId(), result.first, result.second);
        } catch (UndeliverableMessageException e) {
          Log.w("MmsSender", e);
          database.markAsSentFailed(message.getDatabaseMessageId());
          long threadId         = database.getThreadIdForMessage(messageId);
          Recipients recipients = threads.getRecipientsForThreadId(threadId);
          MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
        }
      }
    } catch (MmsException e) {
      Log.w("MmsSender", e);
      if (messageId != -1)
        database.markAsSentFailed(messageId);
    }
  }
}
