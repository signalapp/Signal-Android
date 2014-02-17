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

import org.thoughtcrime.securesms.mms.ImageSlide;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.SendReceiveService;

import java.util.List;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.SendReq;

public class MessageSender {

  public static long sendGroupAction(Context context, MasterSecret masterSecret, Recipients recipients,
                                     long threadId, int groupAction, String groupActionArguments, byte[] avatar)
      throws MmsException
  {
    if (threadId == -1) {
      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    }

    PduBody body = new PduBody();

    if (avatar != null) {
      PduPart part = new PduPart();
      part.setData(avatar);
      part.setContentType(ContentType.IMAGE_PNG.getBytes());
      part.setContentId((System.currentTimeMillis()+"").getBytes());
      part.setName(("Image" + System.currentTimeMillis()).getBytes());
      body.addPart(part);
    }

    SendReq sendRequest = new SendReq();
    sendRequest.setDate(System.currentTimeMillis() / 1000L);
    sendRequest.setBody(body);
    sendRequest.setContentType(ContentType.MULTIPART_MIXED.getBytes());
    sendRequest.setGroupAction(groupAction);
    sendRequest.setGroupActionArguments(groupActionArguments);

    sendMms(context, recipients, masterSecret, sendRequest, threadId,
            ThreadDatabase.DistributionTypes.CONVERSATION, true);

    return threadId;
  }

  public static long sendMms(Context context, MasterSecret masterSecret, Recipients recipients,
                             long threadId, SlideDeck slideDeck, String message, int distributionType,
                             boolean secure)
    throws MmsException
  {
    if (threadId == -1)
      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients, distributionType);

    if (message.trim().length() > 0)
      slideDeck.addSlide(new TextSlide(context, message));

    SendReq sendRequest = new SendReq();
    PduBody body        = slideDeck.toPduBody();

    sendRequest.setDate(System.currentTimeMillis() / 1000L);
    sendRequest.setBody(body);
    sendRequest.setContentType(ContentType.MULTIPART_MIXED.getBytes());

//    Recipients secureRecipients   = recipients.getSecureSessionRecipients(context);
//    Recipients insecureRecipients = recipients.getInsecureSessionRecipients(context);

//    for (Recipient secureRecipient : secureRecipients.getRecipientsList()) {
//      sendMms(context, new Recipients(secureRecipient), masterSecret,
//              sendRequest, threadId, !forcePlaintext);
//    }
//
//    if (!insecureRecipients.isEmpty()) {
//      sendMms(context, insecureRecipients, masterSecret, sendRequest, threadId, false);
//    }

    sendMms(context, recipients, masterSecret, sendRequest, threadId, distributionType, secure);

    return threadId;
  }

  public static long send(Context context, MasterSecret masterSecret,
                          OutgoingTextMessage message, long threadId)
  {
    if (threadId == -1)
      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(message.getRecipients());

    List<Long> messageIds = DatabaseFactory.getEncryptingSmsDatabase(context)
        .insertMessageOutbox(masterSecret, threadId, message);


    for (long messageId : messageIds) {
      Log.w("SMSSender", "Got message id for new message: " + messageId);

      Intent intent = new Intent(SendReceiveService.SEND_SMS_ACTION, null,
                                 context, SendReceiveService.class);
      intent.putExtra("message_id", messageId);
      context.startService(intent);
    }

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

  private static void sendMms(Context context, Recipients recipients, MasterSecret masterSecret,
                              SendReq sendRequest, long threadId, int distributionType, boolean secure)
    throws MmsException
  {
    Log.w("MessageSender", "Distribution type: " + distributionType);

    String[] recipientsArray            = recipients.toNumberStringArray(true);
    EncodedStringValue[] encodedNumbers = EncodedStringValue.encodeStrings(recipientsArray);

    if (recipients.isSingleRecipient()) {
      Log.w("MessageSender", "Single recipient!?");
      sendRequest.setTo(encodedNumbers);
    } else if (distributionType == ThreadDatabase.DistributionTypes.BROADCAST) {
      Log.w("MessageSender", "Broadcast...");
      sendRequest.setBcc(encodedNumbers);
    } else if (distributionType == ThreadDatabase.DistributionTypes.CONVERSATION  || distributionType == 0) {
      Log.w("MessageSender", "Conversation...");
      sendRequest.setTo(encodedNumbers);
    }

    long messageId = DatabaseFactory.getMmsDatabase(context)
                       .insertMessageOutbox(masterSecret, sendRequest, threadId, secure);

    Intent intent  = new Intent(SendReceiveService.SEND_MMS_ACTION, null,
                                context, SendReceiveService.class);
    intent.putExtra("message_id", messageId);
    intent.putExtra("thread_id", threadId);

    context.startService(intent);
  }
}
