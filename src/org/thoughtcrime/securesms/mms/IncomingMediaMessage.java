package org.thoughtcrime.securesms.mms;

import android.util.Log;

import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.push.PushAttachmentPointer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

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

  public IncomingMediaMessage(String localNumber, IncomingPushMessage message, List<File> attachments)
      throws IOException
  {
    this.headers = new PduHeaders();
    this.body    = new PduBody();

    this.headers.setEncodedStringValue(new EncodedStringValue(message.getSource()), PduHeaders.FROM);
    this.headers.appendEncodedStringValue(new EncodedStringValue(localNumber), PduHeaders.TO);

    for (String destination : message.getDestinations()) {
      if (!destination.equals(localNumber)) {
        this.headers.appendEncodedStringValue(new EncodedStringValue(destination), PduHeaders.CC);
      }
    }

    this.headers.setLongInteger(message.getTimestampMillis() / 1000, PduHeaders.DATE);

    if (message.getMessageText() != null && message.getMessageText().length() > 0) {
      PduPart text = new PduPart();
      text.setData(message.getMessageText().getBytes());
      text.setContentType("text/plain".getBytes(CharacterSets.MIMENAME_ISO_8859_1));
      body.addPart(text);
    }

    Iterator<PushAttachmentPointer> descriptors = message.getAttachments().iterator();

    if (attachments != null) {
      for (File attachment : attachments) {
        PduPart               media      = new PduPart();
        FileInputStream       fin        = new FileInputStream(attachment);
        byte[]                data       = Util.readFully(fin);
        PushAttachmentPointer descriptor = descriptors.next();

        Log.w("IncomingMediaMessage", "Adding part: " + descriptor.getContentType() + " with length: " + data.length);

        media.setContentType(descriptor.getContentType().getBytes(CharacterSets.MIMENAME_ISO_8859_1));
        media.setData(data);
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
