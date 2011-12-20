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
import java.util.Arrays;
import java.util.LinkedList;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.SessionCipher;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.mms.MmsSendHelper;
import org.thoughtcrime.securesms.mms.PngTransport;
import org.thoughtcrime.securesms.mms.TextTransport;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Hex;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduComposer;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.SendConf;
import ws.com.google.android.mms.pdu.SendReq;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

public class MmsSender extends MmscProcessor {

  private final LinkedList<SendReq[]> pendingMessages = new LinkedList<SendReq[]>();
		
  public MmsSender(Context context) {
    super(context);
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    if (intent.getAction().equals(SendReceiveService.SEND_MMS_ACTION)) {
      long messageId       = intent.getLongExtra("message_id", -1);			
      MmsDatabase database = DatabaseFactory.getEncryptingMmsDatabase(context, masterSecret);

      try {
	SendReq[] sendRequests;
				
	if (messageId == -1) {
	  sendRequests = database.getOutgoingMessages();
	} else {
	  sendRequests    = new SendReq[1];
	  sendRequests[0] = database.getSendRequest(messageId);
	}
				
	if (sendRequests.length > 0)
	  handleSendMms(sendRequests);
				
      } catch (MmsException me) {
	Log.w("MmsSender", me);
	if (messageId != -1)
	  database.markAsSentFailed(messageId);
      }				
    } else if (intent.getAction().equals(SendReceiveService.SEND_MMS_CONNECTIVITY_ACTION)) {
      handleConnectivityChange(masterSecret);
    }
  }
	
  protected void handleConnectivityChange(MasterSecret masterSecret) {
    if (!isConnected())
      return;
		
    if   (!pendingMessages.isEmpty()) handleSendMmsContinued(masterSecret, pendingMessages.remove());
    else                              finishConnectivity();
  }

  private void handleSendMms(SendReq[] sendRequests) {
    if (!isConnectivityPossible()) {
      for (int i=0;i<sendRequests.length;i++)
	DatabaseFactory.getMmsDatabase(context).markAsSentFailed(sendRequests[i].getDatabaseMessageId());
    } else {
      pendingMessages.add(sendRequests);
      issueConnectivityRequest();
    }
  }	
	
  private boolean isInconsistentResponse(SendReq send, SendConf response) {
    Log.w("MmsSenderService", "Comparing: " + Hex.toString(send.getTransactionId()));
    Log.w("MmsSenderService", "With:      " + Hex.toString(response.getTransactionId()));
    return !Arrays.equals(send.getTransactionId(), response.getTransactionId());
  }

  private byte[] getEncryptedPdu(MasterSecret masterSecret, String recipient, byte[] pduBytes) {
    synchronized (SessionCipher.CIPHER_LOCK) {
      SessionCipher cipher = new SessionCipher(context, masterSecret, new Recipient(null, recipient, null), new TextTransport());
      return cipher.encryptMessage(pduBytes);
    }
  }
	
  private SendReq getEncryptedMms(MasterSecret masterSecret, SendReq pdu, long messageId) {
    Log.w("MmsSender", "Sending Secure MMS.");
    EncodedStringValue[] encodedRecipient = pdu.getTo();
    String recipient                      = encodedRecipient[0].getString();
    byte[] pduBytes                       = new PduComposer(context, pdu).make();
    byte[] encryptedPdu                   = getEncryptedPdu(masterSecret, recipient, pduBytes);
    Log.w("MmsSendeR", "Got encrypted bytes: " + encryptedPdu.length);
    PduBody body                          = new PduBody();
    PduPart part                          = new PduPart();

    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setContentType(ContentType.TEXT_PLAIN.getBytes());
    part.setName((System.currentTimeMillis()+"").getBytes());
    part.setData(encryptedPdu);
    body.addPart(part);
    pdu.setSubject(new EncodedStringValue(WirePrefix.calculateEncryptedMmsSubject()));
    pdu.setBody(body);
		
    return pdu;
  }
	
  private void sendMms(MmsDatabase db, SendReq pdu, String number, long messageId, boolean secure) {
    try {
      if (number != null && number.trim().length() != 0)
	pdu.setFrom(new EncodedStringValue(number));
			
      byte[] response = MmsSendHelper.sendMms(context, new PduComposer(context, pdu).make());
      SendConf conf   = (SendConf) new PduParser(response).parse();
	        
      for (int i=0;i<pdu.getBody().getPartsNum();i++) {
	Log.w("MmsSender", "Sent MMS part of content-type: " + new String(pdu.getBody().getPart(i).getContentType()));	        	
      }
	        
      if (conf == null) {
	db.markAsSentFailed(messageId);
	Log.w("MmsSender", "No M-Send.conf received in response to send."); 
	return;
      } else if (conf.getResponseStatus() != PduHeaders.RESPONSE_STATUS_OK) {
	Log.w("MmsSender", "Got bad response: " + conf.getResponseStatus());
	db.updateResponseStatus(messageId, conf.getResponseStatus());
	db.markAsSentFailed(messageId);
	return;
      } else if (isInconsistentResponse(pdu, conf)) {
	db.markAsSentFailed(messageId);
	Log.w("MmsSender", "Got a response for the wrong transaction?");
	return;
      } else { 	       
	Log.w("MmsSender", "Successful send! " + messageId);
	if (!secure)
	  db.markAsSent(messageId, conf.getMessageId(), conf.getResponseStatus());
	else
	  db.markAsSecureSent(messageId, conf.getMessageId(), conf.getResponseStatus());
      }
    } catch (IOException ioe) {
      Log.w("MmsSender", ioe);
      db.markAsSentFailed(messageId);
    }		
  }
	
  private void handleSendMmsContinued(MasterSecret masterSecret, SendReq[] requests) {
    Log.w("MmsSenderService", "Handling MMS send continuation...");
		
    MmsDatabase db = DatabaseFactory.getEncryptingMmsDatabase(context, masterSecret);
    String number  = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();

    for (int i=0;i<requests.length;i++) {
      SendReq request = requests[i];
      long messageId  = request.getDatabaseMessageId();
      long messageBox = request.getDatabaseMessageBox();
			
      if (MmsDatabase.Types.isSecureMmsBox(messageBox))
	request = getEncryptedMms(masterSecret, request, messageId);
			
      sendMms(db, request, number, messageId, MmsDatabase.Types.isSecureMmsBox(messageBox));
    }
		
    if (this.pendingMessages.isEmpty())
      finishConnectivity();
  }

  @Override
    protected String getConnectivityAction() {
    return SendReceiveService.SEND_MMS_CONNECTIVITY_ACTION;
  }

}
