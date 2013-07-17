package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.push.PushAttachment;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.RateLimitException;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.SendReq;

public class PushTransport extends BaseTransport {

  private final Context      context;
  private final MasterSecret masterSecret;

  public PushTransport(Context context, MasterSecret masterSecret) {
    this.context      = context.getApplicationContext();
    this.masterSecret = masterSecret;
  }

  public void deliver(SmsMessageRecord message) throws IOException {
    try {
      String            localNumber = TextSecurePreferences.getLocalNumber(context);
      String            password    = TextSecurePreferences.getPushServerPassword(context);
      PushServiceSocket socket      = new PushServiceSocket(context, localNumber, password);

      String recipientNumber          = message.getIndividualRecipient().getNumber();
      String recipientCanonicalNumber = PhoneNumberFormatter.formatNumber(recipientNumber,
                                                                          localNumber);

      socket.sendMessage(recipientCanonicalNumber, message.getBody().getBody());

      context.sendBroadcast(constructSentIntent(context, message.getId(), message.getType()));
    } catch (RateLimitException e) {
      Log.w("PushTransport", e);
      throw new IOException("Rate limit exceeded.");
    }
  }

  public void deliver(SendReq message, List<String> destinations) throws IOException {
    try {
      String               localNumber = TextSecurePreferences.getLocalNumber(context);
      String               password    = TextSecurePreferences.getPushServerPassword(context);
      PushServiceSocket    socket      = new PushServiceSocket(context, localNumber, password);
      String               messageText = PartParser.getMessageText(message.getBody());
      List<PushAttachment> attachments = getAttachmentsFromBody(message.getBody());

      if (attachments.isEmpty()) socket.sendMessage(destinations, messageText);
      else                       socket.sendMessage(destinations, messageText, attachments);
    } catch (RateLimitException e) {
      Log.w("PushTransport", e);
      throw new IOException("Rate limit exceeded.");
    }
  }

  private List<PushAttachment> getAttachmentsFromBody(PduBody body) {
    List<PushAttachment> attachments = new LinkedList<PushAttachment>();

    for (int i=0;i<body.getPartsNum();i++) {
      String contentType = Util.toIsoString(body.getPart(i).getContentType());

      if (ContentType.isImageType(contentType) ||
          ContentType.isAudioType(contentType) ||
          ContentType.isVideoType(contentType))
      {
        attachments.add(new PushAttachment(contentType, body.getPart(i).getData()));
      }
    }

    return attachments;
  }
}
