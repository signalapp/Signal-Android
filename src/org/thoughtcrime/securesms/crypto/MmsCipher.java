package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.mms.TextTransport;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.push.TextSecureAddress;

import java.io.IOException;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.MultimediaMessagePdu;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduComposer;
import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.RetrieveConf;
import ws.com.google.android.mms.pdu.SendReq;

public class MmsCipher {

  private static final String TAG = MmsCipher.class.getSimpleName();

  private final TextTransport textTransport = new TextTransport();
  private final AxolotlStore axolotlStore;

  public MmsCipher(AxolotlStore axolotlStore) {
    this.axolotlStore = axolotlStore;
  }

  public MultimediaMessagePdu decrypt(Context context, MultimediaMessagePdu pdu)
      throws InvalidMessageException, LegacyMessageException, DuplicateMessageException,
             NoSessionException
  {
    try {
      Recipients    recipients    = RecipientFactory.getRecipientsFromString(context, pdu.getFrom().getString(), false);
      long          recipientId   = recipients.getPrimaryRecipient().getRecipientId();
      AxolotlAddress axolotlAddress = new AxolotlAddress(recipients.getPrimaryRecipient().getNumber(), TextSecureAddress.DEFAULT_DEVICE_ID);
      SessionCipher sessionCipher = new SessionCipher(axolotlStore, axolotlAddress);
      Optional<byte[]> ciphertext = getEncryptedData(pdu);

      if (!ciphertext.isPresent()) {
        throw new InvalidMessageException("No ciphertext present!");
      }

      byte[] decodedCiphertext = textTransport.getDecodedMessage(ciphertext.get());
      byte[] plaintext;

      try {
        plaintext = sessionCipher.decrypt(new WhisperMessage(decodedCiphertext));
      } catch (InvalidMessageException e) {
        // NOTE - For some reason, Sprint seems to append a single character to the
        // end of message text segments.  I don't know why, so here we just try
        // truncating the message by one if the MAC fails.
        if (ciphertext.get().length > 2) {
          Log.w(TAG, "Attempting truncated decrypt...");
          byte[] truncated = Util.trim(ciphertext.get(), ciphertext.get().length - 1);
          decodedCiphertext = textTransport.getDecodedMessage(truncated);
          plaintext = sessionCipher.decrypt(new WhisperMessage(decodedCiphertext));
        } else {
          throw e;
        }
      }

      MultimediaMessagePdu plaintextGenericPdu = (MultimediaMessagePdu) new PduParser(plaintext).parse();
      return new RetrieveConf(plaintextGenericPdu.getPduHeaders(), plaintextGenericPdu.getBody());
    } catch (IOException e) {
      throw new InvalidMessageException(e);
    }
  }

  public SendReq encrypt(Context context, SendReq message)
      throws NoSessionException, RecipientFormattingException, UndeliverableMessageException
  {
    EncodedStringValue[] encodedRecipient = message.getTo();
    String               recipientString  = encodedRecipient[0].getString();
    Recipients           recipients       = RecipientFactory.getRecipientsFromString(context, recipientString, false);
    long                 recipientId      = recipients.getPrimaryRecipient().getRecipientId();
    byte[]               pduBytes         = new PduComposer(context, message).make();
    AxolotlAddress       axolotlAddress   = new AxolotlAddress(recipients.getPrimaryRecipient().getNumber(), TextSecureAddress.DEFAULT_DEVICE_ID);

    if (pduBytes == null) {
      throw new UndeliverableMessageException("PDU composition failed, null payload");
    }

    if (!axolotlStore.containsSession(axolotlAddress)) {
      throw new NoSessionException("No session for: " + recipientId);
    }

    SessionCipher     cipher            = new SessionCipher(axolotlStore, axolotlAddress);
    CiphertextMessage ciphertextMessage = cipher.encrypt(pduBytes);
    byte[]            encryptedPduBytes = textTransport.getEncodedMessage(ciphertextMessage.serialize());

    PduBody body         = new PduBody();
    PduPart part         = new PduPart();
    SendReq encryptedPdu = new SendReq(message.getPduHeaders(), body);

    part.setContentId((System.currentTimeMillis()+"").getBytes());
    part.setContentType(ContentType.TEXT_PLAIN.getBytes());
    part.setName((System.currentTimeMillis()+"").getBytes());
    part.setData(encryptedPduBytes);
    body.addPart(part);
    encryptedPdu.setSubject(new EncodedStringValue(WirePrefix.calculateEncryptedMmsSubject()));
    encryptedPdu.setBody(body);

    return encryptedPdu;
  }


  private Optional<byte[]> getEncryptedData(MultimediaMessagePdu pdu) {
    for (int i=0;i<pdu.getBody().getPartsNum();i++) {
      if (new String(pdu.getBody().getPart(i).getContentType()).equals(ContentType.TEXT_PLAIN)) {
        return Optional.of(pdu.getBody().getPart(i).getData());
      }
    }

    return Optional.absent();
  }


}
