/**
 * Copyright (C) 2013 Open Whisper Systems
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
package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.mms.IncomingMmsConnection;
import org.thoughtcrime.securesms.mms.MmsConnection;
import org.thoughtcrime.securesms.mms.MmsConnection.Apn;
import org.thoughtcrime.securesms.mms.OutgoingMmsConnection;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.mms.ApnUnavailableException;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.MmsRadio;
import org.thoughtcrime.securesms.mms.MmsRadioException;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.protocol.WirePrefix;

import java.io.IOException;

import ws.com.google.android.mms.InvalidHeaderValueException;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.NotifyRespInd;
import ws.com.google.android.mms.pdu.PduComposer;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.RetrieveConf;

public class MmsDownloader {

  private final Context                         context;
  private final SendReceiveService.ToastHandler toastHandler;
  private final MmsRadio                        radio;

  public MmsDownloader(Context context, SendReceiveService.ToastHandler toastHandler) {
    this.context      = context;
    this.toastHandler = toastHandler;
    this.radio        = MmsRadio.getInstance(context);
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    if (SendReceiveService.DOWNLOAD_MMS_ACTION.equals(intent.getAction())) {
      handleDownloadMms(masterSecret, intent);
    } else if (SendReceiveService.DOWNLOAD_MMS_PENDING_APN_ACTION.equals(intent.getAction())) {
      handleMmsPendingApnDownloads(masterSecret);
    }
  }

  private void handleMmsPendingApnDownloads(MasterSecret masterSecret) {
    if (!IncomingMmsConnection.isConnectionPossible(context, null))
      return;

    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    MmsDatabase.Reader stalledMmsReader = mmsDatabase.getNotificationsWithDownloadState(masterSecret,
                                                                                        MmsDatabase.Status.DOWNLOAD_APN_UNAVAILABLE);
    while (stalledMmsReader.getNext() != null) {
      NotificationMmsMessageRecord stalledMmsRecord = (NotificationMmsMessageRecord) stalledMmsReader.getCurrent();

      Intent intent = new Intent(SendReceiveService.DOWNLOAD_MMS_ACTION, null, context, SendReceiveService.class);
      intent.putExtra("content_location", new String(stalledMmsRecord.getContentLocation()));
      intent.putExtra("message_id", stalledMmsRecord.getId());
      intent.putExtra("transaction_id", stalledMmsRecord.getTransactionId());
      intent.putExtra("thread_id", stalledMmsRecord.getThreadId());
      intent.putExtra("automatic", true);
      context.startService(intent);
    }

    stalledMmsReader.close();
  }

  private void handleDownloadMms(MasterSecret masterSecret, Intent intent) {
    long        messageId       = intent.getLongExtra("message_id", -1);
    long        threadId        = intent.getLongExtra("thread_id", -1);
    byte[]      transactionId   = intent.getByteArrayExtra("transaction_id");
    String      contentLocation = intent.getStringExtra("content_location");
    boolean     automatic       = intent.getBooleanExtra("automatic", false);
    MmsDatabase database        = DatabaseFactory.getMmsDatabase(context);

    database.markDownloadState(messageId, MmsDatabase.Status.DOWNLOAD_CONNECTING);

    Log.w("MmsDownloader", "Downloading mms at "+ Uri.parse(contentLocation).getHost());

    try {
      if (isCdmaNetwork()) {
        Log.w("MmsDownloader", "Connecting directly...");
        try {
          retrieveAndStore(masterSecret, messageId, threadId, contentLocation,
                           transactionId, false, false);
          return;
        } catch (IOException e) {
          Log.w("MmsDownloader", e);
        }
      }

      Log.w("MmsDownloader", "Changing radio to MMS mode..");
      radio.connect();

      Log.w("MmsDownloader", "Downloading in MMS mode with proxy...");

      try {
        retrieveAndStore(masterSecret, messageId, threadId, contentLocation,
                         transactionId, true, true);
        radio.disconnect();
        return;
      } catch (IOException e) {
        Log.w("MmsDownloader", e);
      }

      Log.w("MmsDownloader", "Downloading in MMS mode without proxy...");

      try {
        retrieveAndStore(masterSecret, messageId, threadId,
                         contentLocation, transactionId, true, false);
        radio.disconnect();
      } catch (IOException e) {
        Log.w("MmsDownloader", e);
        radio.disconnect();
        handleDownloadError(masterSecret, messageId, threadId,
                            MmsDatabase.Status.DOWNLOAD_SOFT_FAILURE,
                            context.getString(R.string.MmsDownloader_error_connecting_to_mms_provider),
                            automatic);
      }

    } catch (ApnUnavailableException e) {
      Log.w("MmsDownloader", e);
      handleDownloadError(masterSecret, messageId, threadId, MmsDatabase.Status.DOWNLOAD_APN_UNAVAILABLE,
                          context.getString(R.string.MmsDownloader_error_reading_mms_settings), automatic);
    } catch (MmsException e) {
      Log.w("MmsDownloader", e);
      handleDownloadError(masterSecret, messageId, threadId,
                          MmsDatabase.Status.DOWNLOAD_HARD_FAILURE,
                          context.getString(R.string.MmsDownloader_error_storing_mms),
                          automatic);
    } catch (MmsRadioException e) {
      Log.w("MmsDownloader", e);
      handleDownloadError(masterSecret, messageId, threadId,
                          MmsDatabase.Status.DOWNLOAD_SOFT_FAILURE,
                          context.getString(R.string.MmsDownloader_error_connecting_to_mms_provider),
                          automatic);
    }
  }

  private void retrieveAndStore(MasterSecret masterSecret, long messageId, long threadId,
                                String contentLocation, byte[] transactionId,
                                boolean radioEnabled, boolean useProxy)
      throws IOException, MmsException, ApnUnavailableException
  {
    Apn                   dbApn      = MmsConnection.getApn(context, radio.getApnInformation());
    Apn                   contentApn = new Apn(contentLocation, dbApn.getProxy(), Integer.toString(dbApn.getPort()));
    IncomingMmsConnection connection = new IncomingMmsConnection(context, contentApn);
    RetrieveConf          retrieved  = connection.retrieve(radioEnabled, useProxy);

    storeRetrievedMms(masterSecret, contentLocation, messageId, threadId, retrieved);
    sendRetrievedAcknowledgement(transactionId, radioEnabled, useProxy);
  }

  private void storeRetrievedMms(MasterSecret masterSecret, String contentLocation,
                                 long messageId, long threadId, RetrieveConf retrieved)
      throws MmsException
  {
    MmsDatabase          database = DatabaseFactory.getMmsDatabase(context);
    IncomingMediaMessage message  = new IncomingMediaMessage(retrieved);

    Pair<Long, Long> messageAndThreadId;

    if (retrieved.getSubject() != null && WirePrefix.isEncryptedMmsSubject(retrieved.getSubject().getString())) {
      messageAndThreadId = database.insertSecureMessageInbox(masterSecret, message,
                                                             contentLocation, threadId);

      if (masterSecret != null)
        DecryptingQueue.scheduleDecryption(context, masterSecret, messageAndThreadId.first,
                                           messageAndThreadId.second, retrieved);

    } else {
      messageAndThreadId = database.insertMessageInbox(masterSecret, message,
                                                       contentLocation, threadId);
    }

    database.delete(messageId);
    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void sendRetrievedAcknowledgement(byte[] transactionId,
                                            boolean usingRadio,
                                            boolean useProxy)
      throws ApnUnavailableException
  {
    try {
      NotifyRespInd notifyResponse = new NotifyRespInd(PduHeaders.CURRENT_MMS_VERSION,
                                                       transactionId,
                                                       PduHeaders.STATUS_RETRIEVED);

      OutgoingMmsConnection connection = new OutgoingMmsConnection(context, radio.getApnInformation(), new PduComposer(context, notifyResponse).make());
      connection.sendNotificationReceived(usingRadio, useProxy);
    } catch (InvalidHeaderValueException e) {
      Log.w("MmsDownloader", e);
    } catch (IOException e) {
      Log.w("MmsDownloader", e);
    }
  }


  private void handleDownloadError(MasterSecret masterSecret, long messageId, long threadId,
                                   int downloadStatus, String error, boolean automatic)
  {
    MmsDatabase db = DatabaseFactory.getMmsDatabase(context);

    db.markDownloadState(messageId, downloadStatus);

    if (automatic) {
      db.markIncomingNotificationReceived(threadId);
      MessageNotifier.updateNotification(context, masterSecret, threadId);
    }

    toastHandler.makeToast(error);
  }

  private boolean isCdmaNetwork() {
    return ((TelephonyManager)context
        .getSystemService(Context.TELEPHONY_SERVICE))
        .getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA;
  }
}
