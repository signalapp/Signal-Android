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

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.SessionCipher;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.mms.MmsSendHelper;
import org.thoughtcrime.securesms.mms.TextTransport;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.SendReceiveService.ToastHandler;
import org.thoughtcrime.securesms.util.Hex;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduComposer;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.SendConf;
import ws.com.google.android.mms.pdu.SendReq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class MmsSender extends MmscProcessor {

  private final LinkedList<SendItem> pendingMessages = new LinkedList<SendItem>();
  private final ToastHandler toastHandler;

  public MmsSender(Context context, ToastHandler toastHandler) {
    super(context);
    this.toastHandler = toastHandler;
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    if (intent.getAction().equals(SendReceiveService.SEND_MMS_ACTION)) {
      long messageId       = intent.getLongExtra("message_id", -1);
      boolean isCdma       = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA;
      MmsDatabase database = DatabaseFactory.getEncryptingMmsDatabase(context, masterSecret);

      try {
        List<SendReq> sendRequests = getOutgoingMessages(masterSecret, messageId);

        for (SendReq sendRequest : sendRequests) {
          handleSendMmsAction(new SendItem(masterSecret, sendRequest, messageId != -1, !isCdma, false));
        }

      } catch (MmsException me) {
        Log.w("MmsSender", me);
        if (messageId != -1)
          database.markAsSentFailed(messageId);
        }
    } else if (intent.getAction().equals(SendReceiveService.SEND_MMS_CONNECTIVITY_ACTION)) {
      handleConnectivityChange();
    }
  }

  private void handleSendMmsAction(SendItem item) {
    if (!isConnectivityPossible()) {
      if (item.targeted) {
        toastHandler
          .obtainMessage(0, context.getString(R.string.MmsSender_currently_unable_to_send_your_mms_message))
          .sendToTarget();
      }

      return;
    }

    if (item.useMmsRadio) sendMmsMessageWithRadioChange(item);
    else                  sendMmsMessage(item);
  }

  private void sendMmsMessageWithRadioChange(SendItem item) {
    Log.w("MmsSender", "Sending MMS with radio change..");
    pendingMessages.add(item);
    issueConnectivityRequest();
  }

  private void sendMmsMessage(SendItem item) {
    Log.w("MmsSender", "Sending MMS SendItem...");
    MmsDatabase db  = DatabaseFactory.getEncryptingMmsDatabase(context, item.masterSecret);
    String number   = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
    long messageId  = item.request.getDatabaseMessageId();
    long messageBox = item.request.getDatabaseMessageBox();
    SendReq request = item.request;


    if (MmsDatabase.Types.isSecureMmsBox(messageBox)) {
      request = getEncryptedMms(item.masterSecret, request, messageId);
    }

    if (number != null && number.trim().length() != 0) {
      request.setFrom(new EncodedStringValue(number));
    }

    try {
      SendConf conf = MmsSendHelper.sendMms(context, new PduComposer(context, request).make(),
                                            getApnInformation(), item.useMmsRadio, item.useProxyIfAvailable);

      for (int i=0;i<request.getBody().getPartsNum();i++) {
        Log.w("MmsSender", "Sent MMS part of content-type: " + new String(request.getBody().getPart(i).getContentType()));
      }

      long threadId         = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId);
      Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(context, threadId);

      if (conf == null) {
        db.markAsSentFailed(messageId);
        MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
        Log.w("MmsSender", "No M-Send.conf received in response to send.");
        return;
      } else if (conf.getResponseStatus() != PduHeaders.RESPONSE_STATUS_OK) {
        Log.w("MmsSender", "Got bad response: " + conf.getResponseStatus());
        db.updateResponseStatus(messageId, conf.getResponseStatus());
        db.markAsSentFailed(messageId);
        MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
        return;
      } else if (isInconsistentResponse(request, conf)) {
        db.markAsSentFailed(messageId);
        MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
        Log.w("MmsSender", "Got a response for the wrong transaction?");
        return;
      } else {
        Log.w("MmsSender", "Successful send! " + messageId);
        if (!MmsDatabase.Types.isSecureMmsBox(messageBox)) {
          db.markAsSent(messageId, conf.getMessageId(), conf.getResponseStatus());
        } else {
          db.markAsSecureSent(messageId, conf.getMessageId(), conf.getResponseStatus());
        }
      }
    } catch (IOException ioe) {
      Log.w("MmsSender", ioe);
      if      (!item.useMmsRadio)         scheduleSendWithMmsRadio(item);
      else if (!item.useProxyIfAvailable) scheduleSendWithMmsRadioAndProxy(item);
      else                                db.markAsSentFailed(messageId);
    }
  }

  private List<SendReq> getOutgoingMessages(MasterSecret masterSecret, long messageId)
      throws MmsException
  {
    MmsDatabase database = DatabaseFactory.getEncryptingMmsDatabase(context, masterSecret);
    List<SendReq> sendRequests;

    if (messageId == -1) {
      sendRequests = Arrays.asList(database.getOutgoingMessages());
    } else {
      sendRequests    = new ArrayList<SendReq>(1);
      sendRequests.add(database.getSendRequest(messageId));
    }

    return sendRequests;
  }

  protected void handleConnectivityChange() {
    if (!isConnected()) {
      if (!isConnectivityPossible() && !pendingMessages.isEmpty()) {
        DatabaseFactory.getMmsDatabase(context).markAsSentFailed(pendingMessages.remove().request.getDatabaseMessageId());
        toastHandler.makeToast(context.getString(R.string.MmsSender_currently_unable_to_send_your_mms_message));
        Log.w("MmsSender", "Unable to send MMS.");
        finishConnectivity();
      }

      return;
    }

    List<SendItem> outgoing = (List<SendItem>)pendingMessages.clone();
    pendingMessages.clear();

    for (SendItem item : outgoing) {
      sendMmsMessage(item);
    }

    if (pendingMessages.isEmpty())
      finishConnectivity();
  }

  private boolean isInconsistentResponse(SendReq send, SendConf response) {
    Log.w("MmsSenderService", "Comparing: " + Hex.toString(send.getTransactionId()));
    Log.w("MmsSenderService", "With:      " + Hex.toString(response.getTransactionId()));
    return !Arrays.equals(send.getTransactionId(), response.getTransactionId());
  }

  private byte[] getEncryptedPdu(MasterSecret masterSecret, String recipient, byte[] pduBytes) {
    synchronized (SessionCipher.CIPHER_LOCK) {
      SessionCipher cipher = new SessionCipher(context, masterSecret, new Recipient(null, recipient, null, null), new TextTransport());
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

  private void scheduleSendWithMmsRadioAndProxy(SendItem item) {
    Log.w("MmsSender", "Falling back to sending MMS with radio and proxy...");
    item.useMmsRadio         = true;
    item.useProxyIfAvailable = true;
    handleSendMmsAction(item);
  }

  private void scheduleSendWithMmsRadio(SendItem item) {
    Log.w("MmsSender", "Falling back to sending MMS with radio only...");
    item.useMmsRadio = true;
    handleSendMmsAction(item);
  }

  @Override
  protected String getConnectivityAction() {
    return SendReceiveService.SEND_MMS_CONNECTIVITY_ACTION;
  }

  private static class SendItem {
    private final MasterSecret masterSecret;

    private boolean useMmsRadio;
    private boolean useProxyIfAvailable;
    private SendReq request;
    private boolean targeted;

    public SendItem(MasterSecret masterSecret, SendReq request,
                    boolean targeted, boolean useMmsRadio,
                    boolean useProxyIfAvailable)
    {
      this.masterSecret        = masterSecret;
      this.request             = request;
      this.targeted            = targeted;
      this.useMmsRadio         = useMmsRadio;
      this.useProxyIfAvailable = useProxyIfAvailable;
    }
  }

}
