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

import java.io.IOException;
import java.util.LinkedList;

import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.mms.MmsDownloadHelper;
import org.thoughtcrime.securesms.protocol.WirePrefix;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.RetrieveConf;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MmsDownloader extends MmscProcessor {

  private final LinkedList<DownloadItem> pendingMessages = new LinkedList<DownloadItem>();
  private final SendReceiveService.ToastHandler toastHandler;
	
  public MmsDownloader(Context context, SendReceiveService.ToastHandler toastHandler) {
    super(context);
    this.toastHandler = toastHandler;
  }
	
  private void handleDownloadMms(DownloadItem item) {
    if (!isConnectivityPossible()) {
      DatabaseFactory.getMmsDatabase(context).markDownloadState(item.getMessageId(), MmsDatabase.Types.DOWNLOAD_NO_CONNECTIVITY);
      toastHandler.makeToast("No connectivity available for MMS download, try again later...");
      Log.w("MmsDownloadService", "Unable to download MMS, please try again later.");
    } else {
      DatabaseFactory.getMmsDatabase(context).markDownloadState(item.getMessageId(), MmsDatabase.Types.DOWNLOAD_CONNECTING);
      pendingMessages.add(item);
      issueConnectivityRequest();
    }
  }
	
  private void handleDownloadMmsContinued(DownloadItem item) {
    Log.w("MmsDownloadService", "Handling MMS download continuation...");
    MmsDatabase mmsDatabase;
		
    if (item.getMasterSecret() == null)
      mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    else
      mmsDatabase = DatabaseFactory.getEncryptingMmsDatabase(context, item.getMasterSecret());
		
		
    try {
      byte[] pdu             = MmsDownloadHelper.retrieveMms(context, item.getContentLocation());
      RetrieveConf retrieved = (RetrieveConf)new PduParser(pdu).parse();

      for (int i=0;i<retrieved.getBody().getPartsNum();i++) {
	Log.w("MmsDownloader", "Sent MMS part of content-type: " + new String(retrieved.getBody().getPart(i).getContentType()));	        	
      }

      if (retrieved == null)
	throw new IOException("Bad retrieved PDU");

			
      if (retrieved.getSubject() != null && WirePrefix.isEncryptedMmsSubject(retrieved.getSubject().getString())) {
	long messageId = mmsDatabase.insertSecureMessageReceived(retrieved, item.getContentLocation(), item.getThreadId());
	if (item.getMasterSecret() != null)
	  DecryptingQueue.scheduleDecryption(context, item.getMasterSecret(), messageId, item.getThreadId(), retrieved);
      } else {
	mmsDatabase.insertMessageReceived(retrieved, item.getContentLocation(), item.getThreadId());				
      }
			
      mmsDatabase.delete(item.getMessageId());

      //			NotifyRespInd notifyResponse = new NotifyRespInd(PduHeaders.CURRENT_MMS_VERSION, item.getTransactionId(), PduHeaders.STATUS_RETRIEVED);
      //			MmsSendHelper.sendMms(context, new PduComposer(context, notifyResponse).make());

      if (this.pendingMessages.isEmpty())
	finishConnectivity();
			
    } catch (IOException e) {
      DatabaseFactory.getMmsDatabase(context).markDownloadState(item.getMessageId(), MmsDatabase.Types.DOWNLOAD_SOFT_FAILURE);
      toastHandler.makeToast("Error connecting to MMS provider...");
      Log.w("MmsDownloader", e);
    } catch (MmsException e) {
      DatabaseFactory.getMmsDatabase(context).markDownloadState(item.getMessageId(), MmsDatabase.Types.DOWNLOAD_HARD_FAILURE);
      toastHandler.makeToast("Error downloading MMS!");
      Log.w("MmsDownloader", e);
    }
  }
	
  protected void handleConnectivityChange() {
    if (!isConnected()) {
      if (!isConnectivityPossible() && !pendingMessages.isEmpty()) {
	DatabaseFactory.getMmsDatabase(context).markDownloadState(pendingMessages.remove().getMessageId(), MmsDatabase.Types.DOWNLOAD_NO_CONNECTIVITY);
	toastHandler.makeToast("No connectivity available for MMS download, try again later...");
	Log.w("MmsDownloadService", "Unable to download MMS, please try again later.");
	finishConnectivity();
      }
			
      return;
    }
		
    if   (!pendingMessages.isEmpty()) handleDownloadMmsContinued(pendingMessages.remove());
    else                              finishConnectivity();
  }
	
  public void process(MasterSecret masterSecret, Intent intent) {
    if (intent.getAction().equals(SendReceiveService.DOWNLOAD_MMS_ACTION)) {
      DownloadItem item = new DownloadItem(intent.getLongExtra("message_id", -1), 
					   intent.getLongExtra("thread_id", -1),
					   intent.getStringExtra("content_location"),
					   intent.getByteArrayExtra("transaction_id"),
					   masterSecret);

      handleDownloadMms(item);			
    } else if (intent.getAction().equals(SendReceiveService.DOWNLOAD_MMS_CONNECTIVITY_ACTION)) {
      handleConnectivityChange();
    }
  }

	
  private class DownloadItem {
    private long threadId;
    private long messageId;
    private byte[] transactionId;
    private String contentLocation;
    private MasterSecret masterSecret;
		
    public DownloadItem(long messageId, long threadId, String contentLocation, byte[] transactionId, MasterSecret masterSecret) {
      this.threadId        = threadId;
      this.messageId       = messageId;
      this.contentLocation = contentLocation;
      this.masterSecret    = masterSecret;
      this.transactionId   = transactionId;
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
  }

  @Override
    protected String getConnectivityAction() {
    return SendReceiveService.DOWNLOAD_MMS_CONNECTIVITY_ACTION;
  }


}
