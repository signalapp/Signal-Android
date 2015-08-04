package org.thoughtcrime.securesms.crypto;

import android.content.Context;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage;
import org.thoughtcrime.securesms.sms.IncomingKeyExchangeMessage;
import org.thoughtcrime.securesms.sms.IncomingPreKeyBundleMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.OutgoingKeyExchangeMessage;
import org.thoughtcrime.securesms.sms.OutgoingPrekeyBundleMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.SessionBuilder;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.StaleKeyExchangeException;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.KeyExchangeMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.textsecure.api.push.TextSecureAddress;

import java.io.IOException;

public class SmsCipher {

  private final SmsTransportDetails transportDetails = new SmsTransportDetails();

  private final AxolotlStore axolotlStore;

  public SmsCipher(AxolotlStore axolotlStore) {
    this.axolotlStore = axolotlStore;
  }

  public IncomingTextMessage decrypt(Context context, IncomingTextMessage message)
      throws LegacyMessageException, InvalidMessageException,
             DuplicateMessageException, NoSessionException
  {
    try {
      Recipients     recipients     = RecipientFactory.getRecipientsFromString(context, message.getSender(), false);
      long           recipientId    = recipients.getPrimaryRecipient().getRecipientId();
      byte[]         decoded        = transportDetails.getDecodedMessage(message.getMessageBody().getBytes());
      WhisperMessage whisperMessage = new WhisperMessage(decoded);
      AxolotlAddress axolotlAddress = new AxolotlAddress(recipients.getPrimaryRecipient().getNumber(), TextSecureAddress.DEFAULT_DEVICE_ID);
      SessionCipher  sessionCipher  = new SessionCipher(axolotlStore, axolotlAddress);
      byte[]         padded         = sessionCipher.decrypt(whisperMessage);
      byte[]         plaintext      = transportDetails.getStrippedPaddingMessageBody(padded);

      if (message.isEndSession() && "TERMINATE".equals(new String(plaintext))) {
        axolotlStore.deleteSession(axolotlAddress);
      }

      return message.withMessageBody(new String(plaintext));
    } catch (IOException e) {
      throw new InvalidMessageException(e);
    }
  }

  public IncomingEncryptedMessage decrypt(Context context, IncomingPreKeyBundleMessage message)
      throws InvalidVersionException, InvalidMessageException, DuplicateMessageException,
             UntrustedIdentityException, LegacyMessageException
  {
    try {
      Recipients           recipients    = RecipientFactory.getRecipientsFromString(context, message.getSender(), false);
      byte[]               decoded       = transportDetails.getDecodedMessage(message.getMessageBody().getBytes());
      PreKeyWhisperMessage preKeyMessage = new PreKeyWhisperMessage(decoded);
      AxolotlAddress axolotlAddress      = new AxolotlAddress(recipients.getPrimaryRecipient().getNumber(), TextSecureAddress.DEFAULT_DEVICE_ID);
      SessionCipher        sessionCipher = new SessionCipher(axolotlStore, axolotlAddress);
      byte[]               padded        = sessionCipher.decrypt(preKeyMessage);
      byte[]               plaintext     = transportDetails.getStrippedPaddingMessageBody(padded);

      return new IncomingEncryptedMessage(message, new String(plaintext));
    } catch (IOException | InvalidKeyException | InvalidKeyIdException e) {
      throw new InvalidMessageException(e);
    }
  }

  public OutgoingTextMessage encrypt(OutgoingTextMessage message) throws NoSessionException {
    byte[] paddedBody  = transportDetails.getPaddedMessageBody(message.getMessageBody().getBytes());
    long   recipientId = message.getRecipients().getPrimaryRecipient().getRecipientId();
    AxolotlAddress axolotlAddress      = new AxolotlAddress(message.getRecipients().getPrimaryRecipient().getNumber(), TextSecureAddress.DEFAULT_DEVICE_ID);

    if (!axolotlStore.containsSession(axolotlAddress)) {
      throw new NoSessionException("No session for: " + recipientId);
    }

    SessionCipher     cipher            = new SessionCipher(axolotlStore, axolotlAddress);
    CiphertextMessage ciphertextMessage = cipher.encrypt(paddedBody);
    String            encodedCiphertext = new String(transportDetails.getEncodedMessage(ciphertextMessage.serialize()));

    if (ciphertextMessage.getType() == CiphertextMessage.PREKEY_TYPE) {
      return new OutgoingPrekeyBundleMessage(message, encodedCiphertext);
    } else {
      return message.withBody(encodedCiphertext);
    }
  }

  public OutgoingKeyExchangeMessage process(Context context, IncomingKeyExchangeMessage message)
      throws UntrustedIdentityException, StaleKeyExchangeException,
             InvalidVersionException, LegacyMessageException, InvalidMessageException
  {
    try {
      Recipient          recipient       = RecipientFactory.getRecipientsFromString(context, message.getSender(), false).getPrimaryRecipient();
      KeyExchangeMessage exchangeMessage = new KeyExchangeMessage(transportDetails.getDecodedMessage(message.getMessageBody().getBytes()));
      AxolotlAddress axolotlAddress      = new AxolotlAddress(recipient.getNumber(), TextSecureAddress.DEFAULT_DEVICE_ID);
      SessionBuilder     sessionBuilder  = new SessionBuilder(axolotlStore, axolotlAddress);

      KeyExchangeMessage response        = sessionBuilder.process(exchangeMessage);

      if (response != null) {
        byte[] serializedResponse = transportDetails.getEncodedMessage(response.serialize());
        return new OutgoingKeyExchangeMessage(recipient, new String(serializedResponse));
      } else {
        return null;
      }
    } catch (IOException | InvalidKeyException e) {
      throw new InvalidMessageException(e);
    }
  }

}
