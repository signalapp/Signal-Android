package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.text.TextUtils;
import org.thoughtcrime.securesms.logging.Log;
import android.webkit.MimeTypeMap;

import com.android.mms.dom.smil.parser.SmilXmlSerializer;
import com.google.android.mms.ContentType;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.pdu_alt.CharacterSets;
import com.google.android.mms.pdu_alt.EncodedStringValue;
import com.google.android.mms.pdu_alt.PduBody;
import com.google.android.mms.pdu_alt.PduComposer;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduPart;
import com.google.android.mms.pdu_alt.SendConf;
import com.google.android.mms.pdu_alt.SendReq;
import com.google.android.mms.smil.SmilHelper;
import com.klinker.android.send_message.Utils;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.jobmanager.requirements.NetworkRequirement;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.mms.CompatMmsConnection;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.MmsSendResult;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.transport.InsecureFallbackApprovalException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.NumberUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MmsSendJob extends SendJob {

  private static final long serialVersionUID = 0L;

  private static final String TAG = MmsSendJob.class.getSimpleName();

  private final long messageId;

  public MmsSendJob(Context context, long messageId) {
    super(context, JobParameters.newBuilder()
                                .withGroupId("mms-operation")
                                .withRequirement(new NetworkRequirement(context))
                                .withRequirement(new MasterSecretRequirement(context))
                                .withPersistence()
                                .create());

    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    Log.i(TAG, "onAdded() messageId: " + messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret) throws MmsException, NoSuchMessageException, IOException {
    MmsDatabase          database = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage message  = database.getOutgoingMessage(messageId);

    try {
      Log.i(TAG, "Sending message: " + messageId);

      SendReq pdu = constructSendPdu(message);

      validateDestinations(message, pdu);

      final byte[]        pduBytes = getPduBytes(pdu);
      final SendConf      sendConf = new CompatMmsConnection(context).send(pduBytes, message.getSubscriptionId());
      final MmsSendResult result   = getSendResult(sendConf, pdu);

      database.markAsSent(messageId, false);
      markAttachmentsUploaded(messageId, message.getAttachments());

      Log.i(TAG, "Sent message: " + messageId);
    } catch (UndeliverableMessageException | IOException e) {
      Log.w(TAG, e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (InsecureFallbackApprovalException e) {
      Log.w(TAG, e);
      database.markAsPendingInsecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {
    Log.i(TAG, "onCanceled() messageId: " + messageId);
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }

  private byte[] getPduBytes(SendReq message)
      throws IOException, UndeliverableMessageException, InsecureFallbackApprovalException
  {
    byte[] pduBytes = new PduComposer(context, message).make();

    if (pduBytes == null) {
      throw new UndeliverableMessageException("PDU composition failed, null payload");
    }

    return pduBytes;
  }

  private MmsSendResult getSendResult(SendConf conf, SendReq message)
      throws UndeliverableMessageException
  {
    if (conf == null) {
      throw new UndeliverableMessageException("No M-Send.conf received in response to send.");
    } else if (conf.getResponseStatus() != PduHeaders.RESPONSE_STATUS_OK) {
      throw new UndeliverableMessageException("Got bad response: " + conf.getResponseStatus());
    } else if (isInconsistentResponse(message, conf)) {
      throw new UndeliverableMessageException("Mismatched response!");
    } else {
      return new MmsSendResult(conf.getMessageId(), conf.getResponseStatus());
    }
  }

  private boolean isInconsistentResponse(SendReq message, SendConf response) {
    Log.i(TAG, "Comparing: " + Hex.toString(message.getTransactionId()));
    Log.i(TAG, "With:      " + Hex.toString(response.getTransactionId()));
    return !Arrays.equals(message.getTransactionId(), response.getTransactionId());
  }

  private void validateDestinations(EncodedStringValue[] destinations) throws UndeliverableMessageException {
    if (destinations == null) return;

    for (EncodedStringValue destination : destinations) {
      if (destination == null || !NumberUtil.isValidSmsOrEmail(destination.getString())) {
        throw new UndeliverableMessageException("Invalid destination: " +
                                                (destination == null ? null : destination.getString()));
      }
    }
  }

  private void validateDestinations(OutgoingMediaMessage media, SendReq message) throws UndeliverableMessageException {
    validateDestinations(message.getTo());
    validateDestinations(message.getCc());
    validateDestinations(message.getBcc());

    if (message.getTo() == null && message.getCc() == null && message.getBcc() == null) {
      throw new UndeliverableMessageException("No to, cc, or bcc specified!");
    }

    if (media.isSecure()) {
      throw new UndeliverableMessageException("Attempt to send encrypted MMS?");
    }
  }

  private SendReq constructSendPdu(OutgoingMediaMessage message)
      throws UndeliverableMessageException
  {
    SendReq          req               = new SendReq();
    String           lineNumber        = getMyNumber(context);
    Address          destination       = message.getRecipient().getAddress();
    MediaConstraints mediaConstraints  = MediaConstraints.getMmsMediaConstraints(message.getSubscriptionId());
    List<Attachment> scaledAttachments = scaleAndStripExifFromAttachments(mediaConstraints, message.getAttachments());

    if (!TextUtils.isEmpty(lineNumber)) {
      req.setFrom(new EncodedStringValue(lineNumber));
    } else {
      req.setFrom(new EncodedStringValue(TextSecurePreferences.getLocalNumber(context)));
    }

    if (destination.isMmsGroup()) {
      List<Recipient> members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(destination.toGroupString(), false);

      for (Recipient member : members) {
        if (message.getDistributionType() == ThreadDatabase.DistributionTypes.BROADCAST) {
          req.addBcc(new EncodedStringValue(member.getAddress().serialize()));
        } else {
          req.addTo(new EncodedStringValue(member.getAddress().serialize()));
        }
      }
    } else {
      req.addTo(new EncodedStringValue(destination.serialize()));
    }

    req.setDate(System.currentTimeMillis() / 1000);

    PduBody body = new PduBody();
    int     size = 0;

    if (!TextUtils.isEmpty(message.getBody())) {
      PduPart part = new PduPart();
      String name = String.valueOf(System.currentTimeMillis());
      part.setData(Util.toUtf8Bytes(message.getBody()));
      part.setCharset(CharacterSets.UTF_8);
      part.setContentType(ContentType.TEXT_PLAIN.getBytes());
      part.setContentId(name.getBytes());
      part.setContentLocation((name + ".txt").getBytes());
      part.setName((name + ".txt").getBytes());

      body.addPart(part);
      size += getPartSize(part);
    }

    for (Attachment attachment : scaledAttachments) {
      try {
        if (attachment.getDataUri() == null) throw new IOException("Assertion failed, attachment for outgoing MMS has no data!");

        String  fileName = attachment.getFileName();
        PduPart part     = new PduPart();

        if (fileName == null) {
          fileName      = String.valueOf(Math.abs(Util.getSecureRandom().nextLong()));
          String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(attachment.getContentType());

          if (fileExtension != null) fileName = fileName + "." + fileExtension;
        }

        if (attachment.getContentType().startsWith("text")) {
          part.setCharset(CharacterSets.UTF_8);
        }

        part.setContentType(attachment.getContentType().getBytes());
        part.setContentLocation(fileName.getBytes());
        part.setName(fileName.getBytes());

        int index = fileName.lastIndexOf(".");
        String contentId = (index == -1) ? fileName : fileName.substring(0, index);
        part.setContentId(contentId.getBytes());
        part.setData(Util.readFully(PartAuthority.getAttachmentStream(context, attachment.getDataUri())));

        body.addPart(part);
        size += getPartSize(part);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(body), out);
    PduPart smilPart = new PduPart();
    smilPart.setContentId("smil".getBytes());
    smilPart.setContentLocation("smil.xml".getBytes());
    smilPart.setContentType(ContentType.APP_SMIL.getBytes());
    smilPart.setData(out.toByteArray());
    body.addPart(0, smilPart);

    req.setBody(body);
    req.setMessageSize(size);
    req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes());
    req.setExpiry(7 * 24 * 60 * 60);

    try {
      req.setPriority(PduHeaders.PRIORITY_NORMAL);
      req.setDeliveryReport(PduHeaders.VALUE_NO);
      req.setReadReport(PduHeaders.VALUE_NO);
    } catch (InvalidHeaderValueException e) {}

    return req;
  }

  private long getPartSize(PduPart part) {
    return part.getName().length + part.getContentLocation().length +
        part.getContentType().length + part.getData().length +
        part.getContentId().length;
  }

  private void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long      threadId  = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId);
    Recipient recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);

    if (recipient != null) {
      MessageNotifier.notifyMessageDeliveryFailed(context, recipient, threadId);
    }
  }

  private String getMyNumber(Context context) throws UndeliverableMessageException {
    try {
      return Utils.getMyPhoneNumber(context);
    } catch (SecurityException e) {
      throw new UndeliverableMessageException(e);
    }
  }
}
