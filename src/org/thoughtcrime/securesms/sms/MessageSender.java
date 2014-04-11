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
package org.thoughtcrime.securesms.sms;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.service.SendReceiveService;

import java.util.List;

import ws.com.google.android.mms.MmsException;

public class MessageSender {

  public static long send(Context context, MasterSecret masterSecret,
                          OutgoingTextMessage message, long threadId, boolean forceSms)
  {
    if (threadId == -1)
      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(message.getRecipients());

    List<Long> messageIds = DatabaseFactory.getEncryptingSmsDatabase(context)
        .insertMessageOutbox(masterSecret, threadId, message, forceSms);

    for (long messageId : messageIds) {
      Log.w("SMSSender", "Got message id for new message: " + messageId);

      Intent intent = new Intent(SendReceiveService.SEND_SMS_ACTION, null,
                                 context, SendReceiveService.class);
      intent.putExtra("message_id", messageId);
      context.startService(intent);
    }

    return threadId;
  }

  public static long send(Context context, MasterSecret masterSecret, OutgoingMediaMessage message, long threadId)
      throws MmsException
  {
    if (threadId == -1)
      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(message.getRecipients(), message.getDistributionType());

    long messageId = DatabaseFactory.getMmsDatabase(context)
                                    .insertMessageOutbox(masterSecret, message, threadId);

    Intent intent  = new Intent(SendReceiveService.SEND_MMS_ACTION, null,
                                context, SendReceiveService.class);
    intent.putExtra("message_id", messageId);
    intent.putExtra("thread_id", threadId);

    context.startService(intent);

    return threadId;
  }

  public static void resend(Context context, long messageId, boolean isMms)
  {

    Intent intent;
    if (isMms) {
      DatabaseFactory.getMmsDatabase(context).markAsSending(messageId);
      intent  = new Intent(SendReceiveService.SEND_MMS_ACTION, null,
                           context, SendReceiveService.class);
    } else {
      DatabaseFactory.getSmsDatabase(context).markAsSending(messageId);
      intent  = new Intent(SendReceiveService.SEND_SMS_ACTION, null,
                           context, SendReceiveService.class);
    }
    intent.putExtra("message_id", messageId);
    context.startService(intent);
  }

}
