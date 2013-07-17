package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.SessionCipher;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.mms.MmsRadio;
import org.thoughtcrime.securesms.mms.MmsRadioException;
import org.thoughtcrime.securesms.mms.MmsSendHelper;
import org.thoughtcrime.securesms.mms.TextTransport;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Hex;

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

  public Pair<byte[], Integer> deliver(SendReq message) throws UndeliverableMessageException {
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
        Pair<byte[], Integer> result = sendMms(message, true, false);
        radio.disconnect();
        return result;
      } catch (IOException e) {
        Log.w("MmsTransport", e);
      }

      Log.w("MmsTransport", "Sending MMS with radio change and proxy...");

      try {
        Pair<byte[], Integer> result = sendMms(message, true, true);
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

  private Pair<byte[], Integer> sendMms(SendReq message, boolean usingMmsRadio, boolean useProxy)
      throws IOException, UndeliverableMessageException
  {
    String number = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();

    if (MmsDatabase.Types.isSecureType(message.getDatabaseMessageBox())) {
      message = getEncryptedMessage(message);
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
      return new Pair<byte[], Integer>(conf.getMessageId(), conf.getResponseStatus());
    }
  }

  private SendReq getEncryptedMessage(SendReq pdu) {
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

  private byte[] getEncryptedPdu(MasterSecret masterSecret, String recipient, byte[] pduBytes) {
    synchronized (SessionCipher.CIPHER_LOCK) {
      SessionCipher cipher = new SessionCipher(context, masterSecret,
                                               new Recipient(null, recipient, null, null),
                                               new TextTransport());

      return cipher.encryptMessage(pduBytes);
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

}
