package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MmsCipher;
import org.thoughtcrime.securesms.crypto.storage.TextSecureAxolotlStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.mms.ApnUnavailableException;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.MmsRadio;
import org.thoughtcrime.securesms.mms.MmsRadioException;
import org.thoughtcrime.securesms.mms.MmsSendResult;
import org.thoughtcrime.securesms.mms.OutgoingMmsConnection;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.transport.InsecureFallbackApprovalException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.NumberUtil;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libaxolotl.NoSessionException;

import java.io.IOException;
import java.util.Arrays;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduComposer;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.SendConf;
import ws.com.google.android.mms.pdu.SendReq;

public class MmsSendJob extends SendJob {

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

  }

  @Override
  public void onSend(MasterSecret masterSecret) throws MmsException, NoSuchMessageException, IOException {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    SendReq     message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      MmsSendResult result = deliver(masterSecret, message);

      if (result.isUpgradedSecure()) {
        database.markAsSecure(messageId);
      }

      database.markAsSent(messageId, result.getMessageId(), result.getResponseStatus());
    } catch (UndeliverableMessageException e) {
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
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }

  public MmsSendResult deliver(MasterSecret masterSecret, SendReq message)
      throws UndeliverableMessageException, InsecureFallbackApprovalException
  {

    validateDestinations(message);

    MmsRadio radio = MmsRadio.getInstance(context);

    try {
      if (isCdmaDevice()) {
        Log.w(TAG, "Sending MMS directly without radio change...");
        try {
          return sendMms(masterSecret, radio, message, false, false);
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      Log.w(TAG, "Sending MMS with radio change and proxy...");
      radio.connect();

      try {
        MmsSendResult result = sendMms(masterSecret, radio, message, true, true);
        radio.disconnect();
        return result;
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      Log.w(TAG, "Sending MMS with radio change and without proxy...");

      try {
        MmsSendResult result = sendMms(masterSecret, radio, message, true, false);
        radio.disconnect();
        return result;
      } catch (IOException ioe) {
        Log.w(TAG, ioe);
        radio.disconnect();
        throw new UndeliverableMessageException(ioe);
      }

    } catch (MmsRadioException mre) {
      Log.w(TAG, mre);
      throw new UndeliverableMessageException(mre);
    }
  }

  private MmsSendResult sendMms(MasterSecret masterSecret, MmsRadio radio, SendReq message,
                                boolean usingMmsRadio, boolean useProxy)
      throws IOException, UndeliverableMessageException, InsecureFallbackApprovalException
  {
    String  number         = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
    boolean upgradedSecure = false;

    if (MmsDatabase.Types.isSecureType(message.getDatabaseMessageBox())) {
      message        = getEncryptedMessage(masterSecret, message);
      upgradedSecure = true;
    }

    if (number != null && number.trim().length() != 0) {
      message.setFrom(new EncodedStringValue(number));
    }

    prepareMessageMedia(masterSecret, message, MediaConstraints.MMS_CONSTRAINTS, true);

    try {
      OutgoingMmsConnection connection = new OutgoingMmsConnection(context, radio.getApnInformation(), new PduComposer(context, message).make());
      SendConf              conf       = connection.send(usingMmsRadio, useProxy);

      if (conf == null) {
        throw new UndeliverableMessageException("No M-Send.conf received in response to send.");
      } else if (conf.getResponseStatus() != PduHeaders.RESPONSE_STATUS_OK) {
        throw new UndeliverableMessageException("Got bad response: " + conf.getResponseStatus());
      } else if (isInconsistentResponse(message, conf)) {
        throw new UndeliverableMessageException("Mismatched response!");
      } else {
        return new MmsSendResult(conf.getMessageId(), conf.getResponseStatus(), upgradedSecure, false);
      }
    } catch (ApnUnavailableException aue) {
      throw new IOException("no APN was retrievable");
    }
  }

  private SendReq getEncryptedMessage(MasterSecret masterSecret, SendReq pdu)
      throws InsecureFallbackApprovalException
  {
    try {
      MmsCipher cipher = new MmsCipher(new TextSecureAxolotlStore(context, masterSecret));
      return cipher.encrypt(context, pdu);
    } catch (NoSessionException e) {
      throw new InsecureFallbackApprovalException(e);
    } catch (RecipientFormattingException e) {
      throw new AssertionError(e);
    }
  }

  private boolean isInconsistentResponse(SendReq message, SendConf response) {
    Log.w(TAG, "Comparing: " + Hex.toString(message.getTransactionId()));
    Log.w(TAG, "With:      " + Hex.toString(response.getTransactionId()));
    return !Arrays.equals(message.getTransactionId(), response.getTransactionId());
  }

  private boolean isCdmaDevice() {
    return ((TelephonyManager)context
        .getSystemService(Context.TELEPHONY_SERVICE))
        .getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA;
  }

  private void validateDestination(EncodedStringValue destination) throws UndeliverableMessageException {
    if (destination == null || !NumberUtil.isValidSmsOrEmail(destination.getString())) {
      throw new UndeliverableMessageException("Invalid destination: " +
                                                  (destination == null ? null : destination.getString()));
    }
  }

  private void validateDestinations(SendReq message) throws UndeliverableMessageException {
    if (message.getTo() != null) {
      for (EncodedStringValue to : message.getTo()) {
        validateDestination(to);
      }
    }

    if (message.getCc() != null) {
      for (EncodedStringValue cc : message.getCc()) {
        validateDestination(cc);
      }
    }

    if (message.getBcc() != null) {
      for (EncodedStringValue bcc : message.getBcc()) {
        validateDestination(bcc);
      }
    }

    if (message.getTo() == null && message.getCc() == null && message.getBcc() == null) {
      throw new UndeliverableMessageException("No to, cc, or bcc specified!");
    }
  }

  private void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long       threadId   = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId);
    Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);

    MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
  }



}
