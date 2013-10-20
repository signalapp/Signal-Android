package org.thoughtcrime.securesms.mms;

import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent;
import org.whispersystems.textsecure.util.Base64;

import java.io.UnsupportedEncodingException;

import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.RetrieveConf;

public class IncomingMediaMessage {

  private final PduHeaders headers;
  private final PduBody    body;

  public IncomingMediaMessage(RetrieveConf retreived) {
    this.headers = retreived.getPduHeaders();
    this.body    = retreived.getBody();
  }

  public IncomingMediaMessage(MasterSecret masterSecret, String localNumber,
                              IncomingPushMessage message,
                              PushMessageContent messageContent)
  {
    this.headers = new PduHeaders();
    this.body    = new PduBody();

    this.headers.setEncodedStringValue(new EncodedStringValue(message.getSource()), PduHeaders.FROM);
    this.headers.appendEncodedStringValue(new EncodedStringValue(localNumber), PduHeaders.TO);

    for (String destination : message.getDestinations()) {
      this.headers.appendEncodedStringValue(new EncodedStringValue(destination), PduHeaders.CC);
    }

    this.headers.setLongInteger(message.getTimestampMillis() / 1000, PduHeaders.DATE);

    if (messageContent.getBody() != null && messageContent.getBody().length() > 0) {
      PduPart text = new PduPart();
      text.setData(Util.toIsoBytes(messageContent.getBody()));
      text.setContentType(Util.toIsoBytes("text/plain"));
      body.addPart(text);
    }

    if (messageContent.getAttachmentsCount() > 0) {
      for (PushMessageContent.AttachmentPointer attachment : messageContent.getAttachmentsList()) {
        PduPart media        = new PduPart();
        byte[]  encryptedKey = new MasterCipher(masterSecret).encryptBytes(attachment.getKey().toByteArray());

        media.setContentType(Util.toIsoBytes(attachment.getContentType()));
        media.setContentLocation(Util.toIsoBytes(String.valueOf(attachment.getId())));
        media.setContentDisposition(Util.toIsoBytes(Base64.encodeBytes(encryptedKey)));

        if (message.getRelay() != null) {
          media.setName(Util.toIsoBytes(message.getRelay()));
        }

        media.setPendingPush(true);

        body.addPart(media);
      }
    }
  }

  public PduHeaders getPduHeaders() {
    return headers;
  }

  public PduBody getBody() {
    return body;
  }

  public boolean isGroupMessage() {
    return !Util.isEmpty(headers.getEncodedStringValues(PduHeaders.CC));
  }

}
