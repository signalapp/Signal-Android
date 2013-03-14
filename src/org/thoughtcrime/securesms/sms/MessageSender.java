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
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.KeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.SendReceiveService;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.SendReq;

public class MessageSender {

  public static long sendMms(Context context, MasterSecret masterSecret, Recipients recipients,
                             long threadId, SlideDeck slideDeck, String message, boolean forcePlaintext)
    throws MmsException
  {
    if (threadId == -1)
      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);

    if (message.trim().length() > 0)
      slideDeck.addSlide(new TextSlide(context, message));

    SendReq sendRequest = new SendReq();
    PduBody body        = slideDeck.toPduBody();

    sendRequest.setDate(System.currentTimeMillis() / 1000L);
    sendRequest.setBody(body);
    sendRequest.setContentType(ContentType.MULTIPART_MIXED.getBytes());

    Recipients secureRecipients   = recipients.getSecureSessionRecipients(context);
    Recipients insecureRecipients = recipients.getInsecureSessionRecipients(context);

    for (Recipient secureRecipient : secureRecipients.getRecipientsList()) {
      sendMms(context, new Recipients(secureRecipient), masterSecret,
              sendRequest, threadId, !forcePlaintext);
    }

    if (!insecureRecipients.isEmpty()) {
      sendMms(context, insecureRecipients, masterSecret, sendRequest, threadId, false);
    }

    return threadId;
  }

  public static long send(Context context, MasterSecret masterSecret, Recipients recipients,
                          long threadId, String message, boolean forcePlaintext)
  {
    if (threadId == -1)
      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);

    long date = System.currentTimeMillis();

    for (Recipient recipient : recipients.getRecipientsList()) {
      boolean isSecure = KeyUtil.isSessionFor(context, recipient) && !forcePlaintext;

      long messageId;

      if (!isSecure) {
        messageId = DatabaseFactory.getEncryptingSmsDatabase(context)
                      .insertMessageSent(masterSecret,
                                         PhoneNumberUtils.formatNumber(recipient.getNumber()),
                                         threadId, message, date);
      } else {
        messageId = DatabaseFactory.getEncryptingSmsDatabase(context)
                      .insertSecureMessageSent(masterSecret,
                                               PhoneNumberUtils.formatNumber(recipient.getNumber()),
                                               threadId, message, date);
      }

      Log.w("SMSSender", "Got message id for new message: " + messageId);

      Intent intent = new Intent(SendReceiveService.SEND_SMS_ACTION, null,
                                 context, SendReceiveService.class);
      intent.putExtra("message_id", messageId);
      context.startService(intent);
    }

    return threadId;
  }

  private static void sendMms(Context context, Recipients recipients, MasterSecret masterSecret,
                              SendReq sendRequest, long threadId, boolean secure)
    throws MmsException
  {
    String[] recipientsArray            = recipients.toNumberStringArray(true);
    EncodedStringValue[] encodedNumbers = EncodedStringValue.encodeStrings(recipientsArray);

    sendRequest.setTo(encodedNumbers);

    long messageId = DatabaseFactory.getEncryptingMmsDatabase(context, masterSecret)
                       .insertMessageSent(sendRequest, threadId, secure);

    Intent intent  = new Intent(SendReceiveService.SEND_MMS_ACTION, null,
                                context, SendReceiveService.class);
    intent.putExtra("message_id", messageId);

    context.startService(intent);
  }
}
