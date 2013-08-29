package org.thoughtcrime.securesms.transport;

import android.app.PendingIntent;
import android.content.Context;
import android.telephony.SmsManager;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.sms.OutgoingPrekeyBundleMessage;import org.thoughtcrime.securesms.sms.RawTransportDetails;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.KeyUtil;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.MessageCipher;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MultipartSmsMessageHandler;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.protocol.PreKeyBundleMessage;

import java.util.ArrayList;

public class SmsTransport extends BaseTransport {

  private final Context context;
  private final MasterSecret masterSecret;

  public SmsTransport(Context context, MasterSecret masterSecret) {
    this.context      = context.getApplicationContext();
    this.masterSecret = masterSecret;
  }

  public void deliver(SmsMessageRecord message) throws UndeliverableMessageException {
    if (message.isSecure() || message.isKeyExchange()) {
      deliverSecureMessage(message);
    } else {
      deliverPlaintextMessage(message);
    }
  }

  private void deliverSecureMessage(SmsMessageRecord message) throws UndeliverableMessageException {
    MultipartSmsMessageHandler multipartMessageHandler = new MultipartSmsMessageHandler();
    OutgoingTextMessage transportMessage               = OutgoingTextMessage.from(message);

    if (message.isSecure()) {
      transportMessage = getAsymmetricEncrypt(masterSecret, transportMessage);
    }

    ArrayList<String> messages                = multipartMessageHandler.divideMessage(transportMessage);
    ArrayList<PendingIntent> sentIntents      = constructSentIntents(message.getId(), message.getType(), messages);
    ArrayList<PendingIntent> deliveredIntents = constructDeliveredIntents(message.getId(), message.getType(), messages);

    Log.w("SmsTransport", "Secure divide into message parts: " + messages.size());

    for (int i=0;i<messages.size();i++) {
      // XXX moxie@thoughtcrime.org 1/7/11 -- There's apparently a bug where for some unknown recipients
      // and messages, this will throw an NPE.  I have no idea why, so I'm just catching it and marking
      // the message as a failure.  That way at least it doesn't repeatedly crash every time you start
      // the app.
      try {
        SmsManager.getDefault().sendTextMessage(message.getIndividualRecipient().getNumber(), null, messages.get(i),
                                                sentIntents.get(i),
                                                deliveredIntents == null ? null : deliveredIntents.get(i));
      } catch (NullPointerException npe) {
        Log.w("SmsSender", npe);
        throw new UndeliverableMessageException(npe);
      } catch (IllegalArgumentException iae) {
        Log.w("SmsSender", iae);
        throw new UndeliverableMessageException(iae);
      }
    }

  }

  private void deliverPlaintextMessage(SmsMessageRecord message)
      throws UndeliverableMessageException
  {
    ArrayList<String> messages                = SmsManager.getDefault().divideMessage(message.getBody().getBody());
    ArrayList<PendingIntent> sentIntents      = constructSentIntents(message.getId(), message.getType(), messages);
    ArrayList<PendingIntent> deliveredIntents = constructDeliveredIntents(message.getId(), message.getType(), messages);
    String recipient                          = message.getIndividualRecipient().getNumber();

    // XXX moxie@thoughtcrime.org 1/7/11 -- There's apparently a bug where for some unknown recipients
    // and messages, this will throw an NPE.  I have no idea why, so I'm just catching it and marking
    // the message as a failure.  That way at least it doesn't repeatedly crash every time you start
    // the app.
    try {
      SmsManager.getDefault().sendMultipartTextMessage(recipient, null, messages, sentIntents, deliveredIntents);
    } catch (NullPointerException npe) {
      Log.w("SmsTransport", npe);
      throw new UndeliverableMessageException(npe);
    }
  }

  private ArrayList<PendingIntent> constructSentIntents(long messageId, long type, ArrayList<String> messages) {
    ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>(messages.size());

    for (String message : messages) {
      sentIntents.add(PendingIntent.getBroadcast(context, 0,
                                                 constructSentIntent(context, messageId, type),
                                                 0));
    }

    return sentIntents;
  }

  private ArrayList<PendingIntent> constructDeliveredIntents(long messageId, long type, ArrayList<String> messages) {
    if (!TextSecurePreferences.isSmsDeliveryReportsEnabled(context)) {
      return null;
    }

    ArrayList<PendingIntent> deliveredIntents = new ArrayList<PendingIntent>(messages.size());

    for (String message : messages) {
      deliveredIntents.add(PendingIntent.getBroadcast(context, 0,
                                                      constructDeliveredIntent(context, messageId, type),
                                                      0));
    }

    return deliveredIntents;
  }

  private OutgoingTextMessage getAsymmetricEncrypt(MasterSecret masterSecret,
                                                   OutgoingTextMessage message)
  {
    Recipient       recipient   = message.getRecipients().getPrimaryRecipient();
    String          body        = message.getMessageBody();
    IdentityKeyPair identityKey = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);

    if (KeyUtil.isNonPrekeySessionFor(context, masterSecret, recipient)) {
      Log.w("SmsTransport", "Delivering standard ciphertext...");
      MessageCipher messageCipher = new MessageCipher(context, masterSecret, identityKey, new SmsTransportDetails());
      byte[] ciphertext           = messageCipher.encrypt(recipient, body.getBytes());

      return message.withBody(new String(ciphertext));
    } else {
      Log.w("SmsTransport", "Delivering prekeybundle ciphertext...");
      MessageCipher       messageCipher       = new MessageCipher(context, masterSecret, identityKey, new RawTransportDetails());
      byte[]              bundledMessage      = messageCipher.encrypt(recipient, body.getBytes());
      PreKeyBundleMessage preKeyBundleMessage = new PreKeyBundleMessage(identityKey.getPublicKey(), bundledMessage);

      return new OutgoingPrekeyBundleMessage(message, preKeyBundleMessage.serialize());
    }
  }
}
