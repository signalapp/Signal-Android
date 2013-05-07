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
package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.mms.MmsDownloadHelper;
import org.thoughtcrime.securesms.mms.MmsSendHelper;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.protocol.WirePrefix;

import ws.com.google.android.mms.InvalidHeaderValueException;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.NotifyRespInd;
import ws.com.google.android.mms.pdu.PduComposer;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.RetrieveConf;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class MmsDownloader extends MmscProcessor {

  private final LinkedList<DownloadItem> pendingMessages = new LinkedList<DownloadItem>();
  private final SendReceiveService.ToastHandler toastHandler;

  public MmsDownloader(Context context, SendReceiveService.ToastHandler toastHandler) {
    super(context);
    this.toastHandler = toastHandler;
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    if (intent.getAction().equals(SendReceiveService.DOWNLOAD_MMS_ACTION)) {
      boolean isCdma    = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA;
      DownloadItem item = new DownloadItem(masterSecret, !isCdma, false,
                                           intent.getLongExtra("message_id", -1),
                                           intent.getLongExtra("thread_id", -1),
                                           intent.getBooleanExtra("automatic", false),
                                           intent.getStringExtra("content_location"),
                                           intent.getByteArrayExtra("transaction_id"));

      handleDownloadMmsAction(item);
    } else if (intent.getAction().equals(SendReceiveService.DOWNLOAD_MMS_CONNECTIVITY_ACTION)) {
      handleConnectivityChange();
    }
  }

  private void handleDownloadMmsAction(DownloadItem item) {
    if (!isConnectivityPossible()) {
      Log.w("MmsDownloader", "No MMS connectivity available!");
      handleDownloadError(item, MmsDatabase.Status.DOWNLOAD_NO_CONNECTIVITY,
                          context.getString(R.string.MmsDownloader_no_connectivity_available_for_mms_download_try_again_later));
      return;
    }

    DatabaseFactory.getMmsDatabase(context).markDownloadState(item.getMessageId(), MmsDatabase.Status.DOWNLOAD_CONNECTING);

    if (item.useMmsRadioMode()) downloadMmsWithRadioChange(item);
    else                        downloadMms(item);
  }

  private void downloadMmsWithRadioChange(DownloadItem item) {
    Log.w("MmsDownloader", "Handling MMS download with radio change...");
    pendingMessages.add(item);
    issueConnectivityRequest();
  }

  private void downloadMms(DownloadItem item) {
    Log.w("MmsDownloadService", "Handling actual MMS download...");
    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);

    try {
      RetrieveConf retrieved = MmsDownloadHelper.retrieveMms(context, item.getContentLocation(),
                                                             getApnInformation(),
                                                             item.useMmsRadioMode(),
                                                             item.proxyRequestIfPossible());

      for (int i=0;i<retrieved.getBody().getPartsNum();i++) {
        Log.w("MmsDownloader", "Got MMS part of content-type: " +
              new String(retrieved.getBody().getPart(i).getContentType()));
      }

      storeRetrievedMms(mmsDatabase, item, retrieved);
      sendRetrievedAcknowledgement(item);

    } catch (IOException e) {
      Log.w("MmsDownloader", e);
      if (!item.useMmsRadioMode() && !item.proxyRequestIfPossible()) {
        Log.w("MmsDownloader", "Falling back to just radio mode...");
        scheduleDownloadWithRadioMode(item);
      } else if (!item.proxyRequestIfPossible()) {
        Log.w("MmsDownloadeR", "Falling back to radio mode and proxy...");
        scheduleDownloadWithRadioModeAndProxy(item);
      } else {
        handleDownloadError(item, MmsDatabase.Status.DOWNLOAD_SOFT_FAILURE,
                            context.getString(R.string.MmsDownloader_error_connecting_to_mms_provider));
      }
    } catch (MmsException e) {
      Log.w("MmsDownloader", e);
      handleDownloadError(item, MmsDatabase.Status.DOWNLOAD_HARD_FAILURE,
                          context.getString(R.string.MmsDownloader_error_storing_mms));
    }
  }

  private void storeRetrievedMms(MmsDatabase mmsDatabase, DownloadItem item, RetrieveConf retrieved)
      throws MmsException
  {
    Pair<Long, Long> messageAndThreadId;

    if (retrieved.getSubject() != null && WirePrefix.isEncryptedMmsSubject(retrieved.getSubject().getString())) {
      messageAndThreadId = mmsDatabase.insertSecureMessageInbox(item.getMasterSecret(), retrieved,
                                                                item.getContentLocation(),
                                                                item.getThreadId());

      if (item.getMasterSecret() != null)
        DecryptingQueue.scheduleDecryption(context, item.getMasterSecret(), messageAndThreadId.first,
                                           messageAndThreadId.second, retrieved);

    } else {
      messageAndThreadId = mmsDatabase.insertMessageInbox(item.getMasterSecret(), retrieved,
                                                          item.getContentLocation(),
                                                          item.getThreadId());
    }

    mmsDatabase.delete(item.getMessageId());
    MessageNotifier.updateNotification(context, item.getMasterSecret(), messageAndThreadId.second);
  }

  private void sendRetrievedAcknowledgement(DownloadItem item) {
    try {
      NotifyRespInd notifyResponse = new NotifyRespInd(PduHeaders.CURRENT_MMS_VERSION,
                                                       item.getTransactionId(),
                                                       PduHeaders.STATUS_RETRIEVED);

      MmsSendHelper.sendNotificationReceived(context, new PduComposer(context, notifyResponse).make(),
                                             getApnInformation(), item.useMmsRadioMode(),
                                             item.proxyRequestIfPossible());
    } catch (InvalidHeaderValueException e) {
      Log.w("MmsDownloader", e);
    } catch (IOException e) {
      Log.w("MmsDownloader", e);
    }
  }

  protected void handleConnectivityChange() {
    LinkedList<DownloadItem> downloadItems = (LinkedList<DownloadItem>)pendingMessages.clone();

    if (isConnected()) {
      pendingMessages.clear();

      for (DownloadItem item : downloadItems) {
        downloadMms(item);
      }

      if (pendingMessages.isEmpty())
        finishConnectivity();

    } else if (!isConnected() && (!isConnectivityPossible() || isConnectivityFailure())) {
      pendingMessages.clear();
      handleDownloadError(downloadItems, MmsDatabase.Status.DOWNLOAD_NO_CONNECTIVITY,
                          context.getString(R.string.MmsDownloader_no_connectivity_available_for_mms_download_try_again_later));
      finishConnectivity();
    }
  }

  private void handleDownloadError(List<DownloadItem> items, int downloadStatus, String error) {
    MmsDatabase db = DatabaseFactory.getMmsDatabase(context);

    for (DownloadItem item : items) {
      db.markDownloadState(item.getMessageId(), downloadStatus);

      if (item.isAutomatic()) {
        db.markIncomingNotificationReceived(item.getThreadId());
        MessageNotifier.updateNotification(context, item.getMasterSecret(), item.getThreadId());
      }
    }

    toastHandler.makeToast(error);
  }

  private void handleDownloadError(DownloadItem item, int downloadStatus, String error) {
    MmsDatabase db = DatabaseFactory.getMmsDatabase(context);
    db.markDownloadState(item.getMessageId(), downloadStatus);

    if (item.isAutomatic()) {
      db.markIncomingNotificationReceived(item.getThreadId());
      MessageNotifier.updateNotification(context, item.getMasterSecret(), item.getThreadId());
    }

    toastHandler.makeToast(error);
  }

  private void scheduleDownloadWithRadioMode(DownloadItem item) {
    item.mmsRadioMode = true;
    handleDownloadMmsAction(item);
  }

  private void scheduleDownloadWithRadioModeAndProxy(DownloadItem item) {
    item.mmsRadioMode    = true;
    item.proxyIfPossible = true;
    handleDownloadMmsAction(item);
  }

  private static class DownloadItem {
    private final MasterSecret masterSecret;
    private boolean            mmsRadioMode;
    private boolean            proxyIfPossible;

    private long threadId;
    private long messageId;
    private byte[] transactionId;
    private String contentLocation;
    private boolean automatic;

    public DownloadItem(MasterSecret masterSecret, boolean mmsRadioMode, boolean proxyIfPossible,
                        long messageId, long threadId, boolean automatic, String contentLocation,
                        byte[] transactionId)
    {
      this.masterSecret    = masterSecret;
      this.mmsRadioMode    = mmsRadioMode;
      this.proxyIfPossible = proxyIfPossible;
      this.threadId        = threadId;
      this.messageId       = messageId;
      this.contentLocation = contentLocation;
      this.transactionId   = transactionId;
      this.automatic       = automatic;
    }

    public long getThreadId() {
      return threadId;
    }

    public long getMessageId() {
      return messageId;
    }

    public String getContentLocation() {
      return contentLocation;
    }

    public byte[] getTransactionId() {
      return transactionId;
    }

    public MasterSecret getMasterSecret() {
      return masterSecret;
    }

    public boolean proxyRequestIfPossible() {
      return proxyIfPossible;
    }

    public boolean useMmsRadioMode() {
      return mmsRadioMode;
    }

    public boolean isAutomatic() {
      return automatic;
    }
  }

  @Override
  protected String getConnectivityAction() {
    return SendReceiveService.DOWNLOAD_MMS_CONNECTIVITY_ACTION;
  }
}
