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

package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.mms.MmsRadio;
import org.thoughtcrime.securesms.mms.MmsRadioException;
import org.thoughtcrime.securesms.mms.MmsSendHelper;
import org.thoughtcrime.securesms.mms.MmsSendResult;
import org.thoughtcrime.securesms.mms.TextTransport;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.util.NumberUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.SessionCipher;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.storage.Session;
import org.whispersystems.textsecure.util.Hex;

import java.io.IOException;
import java.util.Arrays;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduComposer;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.SendConf;
import ws.com.google.android.mms.pdu.SendReq;

public class MmsTransport {

  private final Context      context;
  private final MasterSecret masterSecret;
  private final MmsRadio     radio;

  public MmsTransport(Context context, MasterSecret masterSecret) {
    this.context      = context;
    this.masterSecret = masterSecret;
    this.radio        = MmsRadio.getInstance(context);
  }

  public MmsSendResult deliver(SendReq message) throws UndeliverableMessageException,
                                                       InsecureFallbackApprovalException
  {
    if (TextSecurePreferences.isPushRegistered(context) &&
        !TextSecurePreferences.isSmsFallbackEnabled(context))
    {
      throw new UndeliverableMessageException("MMS Transport is not enabled!");
    }

    validateDestinations(message);

    try {
      if (isCdmaDevice()) {
        Log.w("MmsTransport", "Sending MMS directly without radio change...");
        try {
          return sendMms(message, false, false);
        } catch (IOException e) {
          Log.w("MmsTransport", e);
        }
      }

      Log.w("MmsTransport", "Sending MMS with radio change...");
      radio.connect();

      try {
        MmsSendResult result = sendMms(message, true, false);
        radio.disconnect();
        return result;
      } catch (IOException e) {
        Log.w("MmsTransport", e);
      }

      Log.w("MmsTransport", "Sending MMS with radio change and proxy...");

      try {
        MmsSendResult result = sendMms(message, true, true);
        radio.disconnect();
        return result;
      } catch (IOException ioe) {
        Log.w("MmsTransport", ioe);
        radio.disconnect();
        throw new UndeliverableMessageException(ioe);
      }

    } catch (MmsRadioException mre) {
      Log.w("MmsTransport", mre);
      throw new UndeliverableMessageException(mre);
    }
  }

  private MmsSendResult sendMms(SendReq message, boolean usingMmsRadio, boolean useProxy)
      throws IOException, UndeliverableMessageException, InsecureFallbackApprovalException
  {
    String  number         = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
    boolean upgradedSecure = false;

    if (MmsDatabase.Types.isSecureType(message.getDatabaseMessageBox())) {
      message        = getEncryptedMessage(message);
      upgradedSecure = true;
    }

    if (number != null && number.trim().length() != 0) {
      message.setFrom(new EncodedStringValue(number));
    }

    SendConf conf = MmsSendHelper.sendMms(context, new PduComposer(context, message).make(),
                                          radio.getApnInformation(), usingMmsRadio, useProxy);

    for (int i=0;i<message.getBody().getPartsNum();i++) {
      Log.w("MmsSender", "Sent MMS part of content-type: " + new String(message.getBody().getPart(i).getContentType()));
    }

    if (conf == null) {
      throw new UndeliverableMessageException("No M-Send.conf received in response to send.");
    } else if (conf.getResponseStatus() != PduHeaders.RESPONSE_STATUS_OK) {
      throw new UndeliverableMessageException("Got bad response: " + conf.getResponseStatus());
    } else if (isInconsistentResponse(message, conf)) {
      throw new UndeliverableMessageException("Mismatched response!");
    } else {
      return new MmsSendResult(conf.getMessageId(), conf.getResponseStatus(), upgradedSecure, false);
    }
  }

  private SendReq getEncryptedMessage(SendReq pdu) throws InsecureFallbackApprovalException {
    EncodedStringValue[] encodedRecipient = pdu.getTo();
    String recipient                      = encodedRecipient[0].getString();
    byte[] pduBytes                       = new PduComposer(context, pdu).make();
    byte[] encryptedPduBytes              = getEncryptedPdu(masterSecret, recipient, pduBytes);

    PduBody body         = new PduBody();
    PduPart part         = new PduPart();
    SendReq encryptedPdu = new SendReq(pdu.getPduHeaders(), body);

    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setContentType(ContentType.TEXT_PLAIN.getBytes());
    part.setName((System.currentTimeMillis()+"").getBytes());
    part.setData(encryptedPduBytes);
    body.addPart(part);
    encryptedPdu.setSubject(new EncodedStringValue(WirePrefix.calculateEncryptedMmsSubject()));
    encryptedPdu.setBody(body);

    return encryptedPdu;
  }

  private byte[] getEncryptedPdu(MasterSecret masterSecret, String recipientString, byte[] pduBytes) throws InsecureFallbackApprovalException {
    try {
      TextTransport     transportDetails  = new TextTransport();
      Recipient         recipient         = RecipientFactory.getRecipientsFromString(context, recipientString, false).getPrimaryRecipient();
      RecipientDevice   recipientDevice   = new RecipientDevice(recipient.getRecipientId(), RecipientDevice.DEFAULT_DEVICE_ID);

      if (!Session.hasEncryptCapableSession(context, masterSecret, recipient, recipientDevice)) {
        throw new InsecureFallbackApprovalException("No session exists for this secure message.");
      }

      SessionCipher     sessionCipher     = SessionCipher.createFor(context, masterSecret, recipientDevice);
      CiphertextMessage ciphertextMessage = sessionCipher.encrypt(pduBytes);

      return transportDetails.getEncodedMessage(ciphertextMessage.serialize());
    } catch (RecipientFormattingException e) {
      Log.w("MmsTransport", e);
      throw new AssertionError(e);
    }
  }

  private boolean isInconsistentResponse(SendReq message, SendConf response) {
    Log.w("MmsTransport", "Comparing: " + Hex.toString(message.getTransactionId()));
    Log.w("MmsTransport", "With:      " + Hex.toString(response.getTransactionId()));
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

}
