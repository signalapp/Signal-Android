package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.text.TextUtils;

import org.thoughtcrime.securesms.crypto.MasterSecretUnion;
import org.thoughtcrime.securesms.crypto.MediaKey;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;

import java.util.List;

import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduPart;

public class OutgoingMediaMessage {

  private   final Recipients recipients;
  protected final PduBody    body;
  private   final int        distributionType;

  public OutgoingMediaMessage(Context context, Recipients recipients, PduBody body,
                              String message, int distributionType)
  {
    this.recipients       = recipients;
    this.body             = body;
    this.distributionType = distributionType;

    if (!TextUtils.isEmpty(message)) {
      this.body.addPart(new TextSlide(context, message).getPart());
    }
  }

  public OutgoingMediaMessage(Context context, Recipients recipients, SlideDeck slideDeck,
                              String message, int distributionType)
  {
    this(context, recipients, slideDeck.toPduBody(), message, distributionType);
  }

  public OutgoingMediaMessage(Context context, MasterSecretUnion masterSecret,
                              Recipients recipients, List<TextSecureAttachment> attachments,
                              String message)
  {
    this(context, recipients, pduBodyFor(masterSecret, attachments), message,
         ThreadDatabase.DistributionTypes.CONVERSATION);
  }

  public OutgoingMediaMessage(OutgoingMediaMessage that) {
    this.recipients       = that.getRecipients();
    this.body             = that.body;
    this.distributionType = that.distributionType;
  }

  public Recipients getRecipients() {
    return recipients;
  }

  public PduBody getPduBody() {
    return body;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public boolean isSecure() {
    return false;
  }

  public boolean isGroup() {
    return false;
  }

  private static PduBody pduBodyFor(MasterSecretUnion masterSecret, List<TextSecureAttachment> attachments) {
    PduBody body = new PduBody();

    for (TextSecureAttachment attachment : attachments) {
      if (attachment.isPointer()) {
        PduPart media        = new PduPart();
        String  encryptedKey = MediaKey.getEncrypted(masterSecret, attachment.asPointer().getKey());

        media.setContentType(Util.toIsoBytes(attachment.getContentType()));
        media.setContentLocation(Util.toIsoBytes(String.valueOf(attachment.asPointer().getId())));
        media.setContentDisposition(Util.toIsoBytes(encryptedKey));

        body.addPart(media);
      }
    }

    return body;
  }

}
