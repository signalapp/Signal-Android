package org.thoughtcrime.securesms.mms;

import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.Hex;

import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.RetrieveConf;

public class IncomingMediaMessage {

  private final PduHeaders headers;
  private final PduBody    body;
  private final String     groupId;
  private final int        groupAction;
  private final String     groupActionArguments;

  public IncomingMediaMessage(RetrieveConf retreived) {
    this.headers              = retreived.getPduHeaders();
    this.body                 = retreived.getBody();
    this.groupId              = null;
    this.groupAction          = -1;
    this.groupActionArguments = null;
  }

  public IncomingMediaMessage(MasterSecret masterSecret, String localNumber,
                              IncomingPushMessage message,
                              PushMessageContent messageContent)
  {
    this.headers = new PduHeaders();
    this.body    = new PduBody();

    if (messageContent.hasGroup()) {
      this.groupId              = GroupUtil.getEncodedId(messageContent.getGroup().getId().toByteArray());
      this.groupAction          = messageContent.getGroup().getType().getNumber();
      this.groupActionArguments = GroupUtil.serializeArguments(messageContent.getGroup());
    } else {
      this.groupId              = null;
      this.groupAction          = -1;
      this.groupActionArguments = null;
    }

    this.headers.setEncodedStringValue(new EncodedStringValue(message.getSource()), PduHeaders.FROM);
    this.headers.appendEncodedStringValue(new EncodedStringValue(localNumber), PduHeaders.TO);
    this.headers.setLongInteger(message.getTimestampMillis() / 1000, PduHeaders.DATE);

    if (!org.whispersystems.textsecure.util.Util.isEmpty(messageContent.getBody())) {
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

  public String getGroupId() {
    return groupId;
  }

  public boolean isGroupMessage() {
    return groupId != null                                           ||
        !Util.isEmpty(headers.getEncodedStringValues(PduHeaders.CC)) ||
        (headers.getEncodedStringValues(PduHeaders.TO) != null &&
         headers.getEncodedStringValues(PduHeaders.TO).length > 1);
  }

  public int getGroupAction() {
    return groupAction;
  }

  public String getGroupActionArguments() {
    return groupActionArguments;
  }
}
