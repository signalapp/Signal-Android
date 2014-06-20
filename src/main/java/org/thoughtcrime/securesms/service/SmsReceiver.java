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
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessorV2;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.crypto.protocol.KeyExchangeMessage;
import org.whispersystems.textsecure.crypto.LegacyMessageException;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage;
import org.thoughtcrime.securesms.sms.IncomingKeyExchangeMessage;
import org.thoughtcrime.securesms.sms.IncomingPreKeyBundleMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.MultipartSmsMessageHandler;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.protocol.PreKeyWhisperMessage;
import org.whispersystems.textsecure.crypto.protocol.WhisperMessage;
import org.whispersystems.textsecure.storage.InvalidKeyIdException;
import org.whispersystems.textsecure.storage.RecipientDevice;

import java.io.IOException;
import java.util.List;

public class SmsReceiver {

  private MultipartSmsMessageHandler multipartMessageHandler = new MultipartSmsMessageHandler();

  private final Context context;

  public SmsReceiver(Context context) {
    this.context = context;
  }

  private IncomingTextMessage assembleMessageFragments(List<IncomingTextMessage> messages) {
    IncomingTextMessage message = new IncomingTextMessage(messages);

    if (WirePrefix.isEncryptedMessage(message.getMessageBody()) ||
        WirePrefix.isKeyExchange(message.getMessageBody())      ||
        WirePrefix.isPreKeyBundle(message.getMessageBody())     ||
        WirePrefix.isEndSession(message.getMessageBody()))
    {
      return multipartMessageHandler.processPotentialMultipartMessage(message);
    } else {
      return message;
    }
  }

  private Pair<Long, Long> storeSecureMessage(MasterSecret masterSecret, IncomingTextMessage message) {
    Pair<Long, Long> messageAndThreadId = DatabaseFactory.getEncryptingSmsDatabase(context)
                                                         .insertMessageInbox(masterSecret, message);

    if (masterSecret != null) {
      DecryptingQueue.scheduleDecryption(context, masterSecret, messageAndThreadId.first,
                                         messageAndThreadId.second,
                                         message.getSender(), message.getSenderDeviceId(),
                                         message.getMessageBody(), message.isSecureMessage(),
                                         message.isKeyExchange(), message.isEndSession());
    }

    return messageAndThreadId;
  }

  private Pair<Long, Long> storeStandardMessage(MasterSecret masterSecret, IncomingTextMessage message) {
    EncryptingSmsDatabase encryptingDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
    SmsDatabase           plaintextDatabase  = DatabaseFactory.getSmsDatabase(context);

    if (masterSecret != null) {
      return encryptingDatabase.insertMessageInbox(masterSecret, message);
    } else if (MasterSecretUtil.hasAsymmericMasterSecret(context)) {
      return encryptingDatabase.insertMessageInbox(MasterSecretUtil.getAsymmetricMasterSecret(context, null), message);
    } else {
      return plaintextDatabase.insertMessageInbox(message);
    }
  }

  private Pair<Long, Long> storePreKeyWhisperMessage(MasterSecret masterSecret,
                                                     IncomingPreKeyBundleMessage message)
  {
    Log.w("SmsReceiver", "Processing prekey message...");

    try {
      Recipient              recipient        = RecipientFactory.getRecipientsFromString(context, message.getSender(), false).getPrimaryRecipient();
      RecipientDevice        recipientDevice  = new RecipientDevice(recipient.getRecipientId(), message.getSenderDeviceId());
      KeyExchangeProcessorV2 processor        = new KeyExchangeProcessorV2(context, masterSecret, recipientDevice);
      SmsTransportDetails    transportDetails = new SmsTransportDetails();
      byte[]                 rawMessage       = transportDetails.getDecodedMessage(message.getMessageBody().getBytes());
      PreKeyWhisperMessage   preKeyExchange   = new PreKeyWhisperMessage(rawMessage);

      if (processor.isTrusted(preKeyExchange)) {
        processor.processKeyExchangeMessage(preKeyExchange);

        WhisperMessage           ciphertextMessage  = preKeyExchange.getWhisperMessage();
        String                   bundledMessageBody = new String(transportDetails.getEncodedMessage(ciphertextMessage.serialize()));
        IncomingEncryptedMessage bundledMessage     = new IncomingEncryptedMessage(message, bundledMessageBody);
        Pair<Long, Long>         messageAndThreadId = storeSecureMessage(masterSecret, bundledMessage);

        Intent intent = new Intent(KeyExchangeProcessorV2.SECURITY_UPDATE_EVENT);
        intent.putExtra("thread_id", messageAndThreadId.second);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent, KeyCachingService.KEY_PERMISSION);

        return messageAndThreadId;
      }
    } catch (InvalidKeyException e) {
      Log.w("SmsReceiver", e);
      message.setCorrupted(true);
    } catch (InvalidVersionException e) {
      Log.w("SmsReceiver", e);
      message.setInvalidVersion(true);
    } catch (InvalidKeyIdException e) {
      Log.w("SmsReceiver", e);
      message.setStale(true);
    } catch (IOException e) {
      Log.w("SmsReceive", e);
      message.setCorrupted(true);
    } catch (InvalidMessageException e) {
      Log.w("SmsReceiver", e);
      message.setCorrupted(true);
    } catch (RecipientFormattingException e) {
      Log.w("SmsReceiver", e);
      message.setCorrupted(true);
    }

    return storeStandardMessage(masterSecret, message);
  }

  private Pair<Long, Long> storeKeyExchangeMessage(MasterSecret masterSecret,
                                                   IncomingKeyExchangeMessage message)
  {
    if (masterSecret != null && TextSecurePreferences.isAutoRespondKeyExchangeEnabled(context)) {
      try {
        Recipient            recipient       = RecipientFactory.getRecipientsFromString(context, message.getSender(), false).getPrimaryRecipient();
        RecipientDevice      recipientDevice = new RecipientDevice(recipient.getRecipientId(), message.getSenderDeviceId());
        KeyExchangeMessage   exchangeMessage = KeyExchangeMessage.createFor(message.getMessageBody());
        KeyExchangeProcessor processor       = KeyExchangeProcessor.createFor(context, masterSecret, recipientDevice, exchangeMessage);

        if (processor.isStale(exchangeMessage)) {
          message.setStale(true);
        } else if (processor.isTrusted(exchangeMessage)) {
          message.setProcessed(true);

          Pair<Long, Long> messageAndThreadId = storeStandardMessage(masterSecret, message);
          processor.processKeyExchangeMessage(exchangeMessage, messageAndThreadId.second);

          return messageAndThreadId;
        }
      } catch (InvalidVersionException e) {
        Log.w("SmsReceiver", e);
        message.setInvalidVersion(true);
      } catch (InvalidKeyException e) {
        Log.w("SmsReceiver", e);
        message.setCorrupted(true);
      } catch (InvalidMessageException e) {
        Log.w("SmsReceiver", e);
        message.setCorrupted(true);
      } catch (RecipientFormattingException e) {
        Log.w("SmsReceiver", e);
        message.setCorrupted(true);
      } catch (LegacyMessageException e) {
        Log.w("SmsReceiver", e);
        message.setLegacyVersion(true);
      }
    }

    return storeStandardMessage(masterSecret, message);
  }

  private Pair<Long, Long> storeMessage(MasterSecret masterSecret, IncomingTextMessage message) {
    if      (message.isSecureMessage()) return storeSecureMessage(masterSecret, message);
    else if (message.isPreKeyBundle())  return storePreKeyWhisperMessage(masterSecret, (IncomingPreKeyBundleMessage) message);
    else if (message.isKeyExchange())   return storeKeyExchangeMessage(masterSecret, (IncomingKeyExchangeMessage) message);
    else if (message.isEndSession())    return storeSecureMessage(masterSecret, message);
    else                                return storeStandardMessage(masterSecret, message);
  }

  private void handleReceiveMessage(MasterSecret masterSecret, Intent intent) {
    if (intent.getExtras() == null) return;

    List<IncomingTextMessage> messagesList = intent.getExtras().getParcelableArrayList("text_messages");
    IncomingTextMessage       message      = assembleMessageFragments(messagesList);

    if (message != null) {
      Pair<Long, Long> messageAndThreadId = storeMessage(masterSecret, message);
      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
    }
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    if (SendReceiveService.RECEIVE_SMS_ACTION.equals(intent.getAction())) {
      handleReceiveMessage(masterSecret, intent);
    }
  }
}
