package org.thoughtcrime.securesms.jobs;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
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
import org.thoughtcrime.securesms.providers.MmsBodyProvider;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.transport.InsecureFallbackApprovalException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.NumberUtil;
import org.thoughtcrime.securesms.util.SmilUtil;
import org.thoughtcrime.securesms.util.TelephonyUtil;
import org.thoughtcrime.securesms.util.SmilUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduComposer;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.SendConf;
import ws.com.google.android.mms.pdu.SendReq;

public class MmsSendJob extends SendJob {
  private static final String TAG = MmsSendJob.class.getSimpleName();

  private final long messageId;

  private transient MmsSentReceiver mmsSentReceiver;

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
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    database.markAsSending(messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret) throws MmsException, NoSuchMessageException, IOException {
    mmsSentReceiver = new MmsSentReceiver();
    context.getApplicationContext().registerReceiver(mmsSentReceiver, new IntentFilter(MmsSentReceiver.ACTION));
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    SendReq     message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      MmsSendResult result = deliver(masterSecret, message, messageId);
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
    context.getApplicationContext().unregisterReceiver(mmsSentReceiver);
  }

  private MmsSendResult deliver(MasterSecret masterSecret, SendReq message, long messageId)
      throws UndeliverableMessageException, InsecureFallbackApprovalException
  {
    validateDestinations(message);
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      return deliverLollipop(masterSecret, message, messageId);
    } else {
      return deliverLegacy(masterSecret, message);
    }
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  private MmsSendResult deliverLollipop(MasterSecret masterSecret, SendReq message, long messageId)
      throws UndeliverableMessageException, InsecureFallbackApprovalException
  {
    Log.w(TAG, "deliverLollipop()");
    try {
      byte[] pdu = getPduBytes(masterSecret, message);
      return getSendResult(sendLollipopMms(context, pdu, messageId), message);
    } catch (IOException ioe) {
      throw new UndeliverableMessageException(ioe);
    }
  }

  public MmsSendResult deliverLegacy(MasterSecret masterSecret, SendReq message)
      throws UndeliverableMessageException, InsecureFallbackApprovalException
  {
    Log.w(TAG, "deliverLegacy()");
    try {
      MmsRadio radio    = MmsRadio.getInstance(context);
      byte[]   pduBytes = getPduBytes(masterSecret, message);

      if (isCdmaDevice()) {
        Log.w(TAG, "Sending MMS directly without radio change...");
        try {
          return sendMms(radio, message, pduBytes, false, false);
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      Log.w(TAG, "Sending MMS with radio change and proxy...");
      radio.connect();

      try {
        try {
          return sendMms(radio, message, pduBytes, true, true);
        } catch (IOException e) {
          Log.w(TAG, e);
        }

        Log.w(TAG, "Sending MMS with radio change and without proxy...");

        try {
          return sendMms(radio, message, pduBytes, true, false);
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
          throw new UndeliverableMessageException(ioe);
        }
      } finally {
        radio.disconnect();
      }

    } catch (MmsRadioException | IOException e) {
      Log.w(TAG, e);
      throw new UndeliverableMessageException(e);
    }
  }

  private MmsSendResult sendMms(MmsRadio radio, SendReq message, byte[] pduBytes,
                                boolean usingMmsRadio, boolean useProxy)
      throws IOException, UndeliverableMessageException, InsecureFallbackApprovalException
  {

    try {
      OutgoingMmsConnection connection = new OutgoingMmsConnection(context, radio.getApnInformation(), pduBytes);
      return getSendResult(connection.send(usingMmsRadio, useProxy), message);

    } catch (ApnUnavailableException aue) {
      throw new IOException("no APN was retrievable");
    }
  }

  private byte[] getPduBytes(MasterSecret masterSecret, SendReq message)
      throws IOException, UndeliverableMessageException, InsecureFallbackApprovalException
  {
    String number = TelephonyUtil.getManager(context).getLine1Number();

    message = getResolvedMessage(masterSecret, message, MediaConstraints.MMS_CONSTRAINTS, true);
    message.setBody(SmilUtil.getSmilBody(message.getBody()));
    if (MmsDatabase.Types.isSecureType(message.getDatabaseMessageBox())) {
      throw new UndeliverableMessageException("Attempt to send encrypted MMS?");
    }

    if (number != null && number.trim().length() != 0) {
      message.setFrom(new EncodedStringValue(number));
    }
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

    if (recipients != null) {
      MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
    }
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  public SendConf sendLollipopMms(Context context, byte[] pdu, long messageId) throws UndeliverableMessageException {
    try {
      File file = new File(context.getCacheDir(), messageId + ".mmsbody");
      Util.copy(new ByteArrayInputStream(pdu), new FileOutputStream(file));

      SmsManager.getDefault().sendMultimediaMessage(context, ContentUris.withAppendedId(MmsBodyProvider.CONTENT_URI, messageId), null, null,
                                                    PendingIntent.getBroadcast(context, 1, new Intent(MmsSentReceiver.ACTION), PendingIntent.FLAG_ONE_SHOT));
      synchronized (mmsSentReceiver) {
        while (!mmsSentReceiver.isFinished()) Util.wait(mmsSentReceiver, 30000);
      }
      Log.w(TAG, "MMS broadcast received and processed.");
      context.getApplicationContext().unregisterReceiver(mmsSentReceiver);
      byte[] response = mmsSentReceiver.getResponse();

      if (!file.delete()) {
        Log.w(TAG, "couldn't delete " + file.getAbsolutePath());
      }
      return (SendConf) new PduParser(response).parse();
    } catch (IOException ioe) {
      throw new UndeliverableMessageException(ioe);
    }
  }

  public static class MmsSentReceiver extends BroadcastReceiver {
    public static final String ACTION = MmsSendJob.class.getCanonicalName() + "MMS_SENT_ACTION";

    private byte[] response;
    private boolean finished;

    @TargetApi(VERSION_CODES.LOLLIPOP)
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w(TAG, "onReceive()");
      if (!ACTION.equals(intent.getAction())) {
        Log.w(TAG, "received broadcast with unexpected action " + intent.getAction());
        return;
      }
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP_MR1) {
        Log.w(TAG, "HTTP status: " + intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1));
      }

      response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA);
      finished = true;
      synchronized (this) {
        notifyAll();
      }
    }

    public boolean isFinished() {
      return finished;
    }

    public byte[] getResponse() {
      return response;
    }

  }
}
