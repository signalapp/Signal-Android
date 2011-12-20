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

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.InvalidVersionException;
import org.thoughtcrime.securesms.crypto.KeyExchangeMessage;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MultipartMessageHandler;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver {

  private MultipartMessageHandler multipartMessageHandler = new MultipartMessageHandler();
	
  private final Context context;
	
  public SmsReceiver(Context context) {
    this.context = context;
  }
		
  private String assembleSecureMessageFragments(String sender, String messageBody) {
    String localPrefix;
		
    if (WirePrefix.isEncryptedMessage(messageBody)) {
      localPrefix = Prefix.ASYMMETRIC_ENCRYPT;
    } else {
      localPrefix = Prefix.KEY_EXCHANGE;
    }
		
    Log.w("SMSReceiverService", "Calculated local prefix for message: " + messageBody + " - Local Prefix: " + localPrefix);

    messageBody = messageBody.substring(WirePrefix.PREFIX_SIZE);
		
    Log.w("SMSReceiverService", "Parsed off wire prefix: " + messageBody);
		
    if (!multipartMessageHandler.isManualTransport(messageBody))
      return localPrefix + messageBody;
    else
      return multipartMessageHandler.processPotentialMultipartMessage(localPrefix, sender, messageBody);
		
  }
	
  private String assembleMessageFragments(SmsMessage[] messages) {
    StringBuilder body = new StringBuilder();
		
    for (SmsMessage message : messages) {
      body.append(message.getDisplayMessageBody());
    }
		
    String messageBody = body.toString();
		
    if (WirePrefix.isEncryptedMessage(messageBody) || WirePrefix.isKeyExchange(messageBody)) {
      return assembleSecureMessageFragments(messages[0].getDisplayOriginatingAddress(), messageBody);
    } else {
      return messageBody;
    }
  }
		
  private void storeSecureMessage(MasterSecret masterSecret, SmsMessage message, String messageBody) {
    long messageId = DatabaseFactory.getSmsDatabase(context).insertSecureMessageReceived(message, messageBody);
    Log.w("SmsReceiver", "Inserted secure message received: " + messageId);
    if (masterSecret != null) 
      DecryptingQueue.scheduleDecryption(context, masterSecret, messageId, message.getDisplayOriginatingAddress(), messageBody);		
  }
		
  private long storeStandardMessage(MasterSecret masterSecret, SmsMessage message, String messageBody) {
    if      (masterSecret != null)                               return DatabaseFactory.getEncryptingSmsDatabase(context).insertMessageReceived(masterSecret, message, messageBody);
    else if (MasterSecretUtil.hasAsymmericMasterSecret(context)) return DatabaseFactory.getEncryptingSmsDatabase(context).insertMessageReceived(MasterSecretUtil.getAsymmetricMasterSecret(context, null), message, messageBody);
    else                                                         return DatabaseFactory.getSmsDatabase(context).insertMessageReceived(message, messageBody);											
  }

  private void storeKeyExchangeMessage(MasterSecret masterSecret, SmsMessage message, String messageBody) {
    if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ApplicationPreferencesActivity.AUTO_KEY_EXCHANGE_PREF, true)) {
      try {
	Recipient recipient                   = new Recipient(null, message.getDisplayOriginatingAddress(), null);
	KeyExchangeMessage keyExchangeMessage = new KeyExchangeMessage(messageBody);
	KeyExchangeProcessor processor        = new KeyExchangeProcessor(context, masterSecret, recipient);
								
	Log.w("SmsReceiver", "Received key with fingerprint: " + keyExchangeMessage.getPublicKey().getFingerprint());
				
	if (processor.isStale(keyExchangeMessage)) {
	  messageBody    = messageBody.substring(Prefix.KEY_EXCHANGE.length());
	  messageBody    = Prefix.STALE_KEY_EXCHANGE + messageBody;
	} else if (!processor.hasCompletedSession() || processor.hasSameSessionIdentity(keyExchangeMessage)) {
	  messageBody    = messageBody.substring(Prefix.KEY_EXCHANGE.length());
	  messageBody    = Prefix.PROCESSED_KEY_EXCHANGE + messageBody;
	  long messageId = storeStandardMessage(masterSecret, message, messageBody);
	  long threadId  = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageId);
					
	  processor.processKeyExchangeMessage(keyExchangeMessage, threadId);
	  return;
	}			
      } catch (InvalidVersionException e) {
	Log.w("SmsReceiver", e);
      } catch (InvalidKeyException e) {
	Log.w("SmsReceiver", e);
      }
    }

    storeStandardMessage(masterSecret, message, messageBody);
  }
		
  private boolean storeMessage(MasterSecret masterSecret, SmsMessage message, String messageBody) {
    if (messageBody.startsWith(Prefix.ASYMMETRIC_ENCRYPT)) {
      storeSecureMessage(masterSecret, message, messageBody);
    } else if (messageBody.startsWith(Prefix.KEY_EXCHANGE)) {
      storeKeyExchangeMessage(masterSecret, message, messageBody);
    } else {
      storeStandardMessage(masterSecret, message, messageBody);
    }
			
    return true;
  }

  private SmsMessage[] parseMessages(Bundle bundle) {
    Object[] pdus         = (Object[])bundle.get("pdus");
    SmsMessage[] messages = new SmsMessage[pdus.length];
		
    for (int i=0;i<pdus.length;i++)
      messages[i] = SmsMessage.createFromPdu((byte[])pdus[i]);
		
    return messages;
  }
	
  private void handleReceiveMessage(MasterSecret masterSecret, Intent intent) {
    Bundle bundle         = intent.getExtras();
    SmsMessage[] messages = parseMessages(bundle);
    String message        = assembleMessageFragments(messages);
		
    if (message != null) {
      storeMessage(masterSecret, messages[0], message);
      MessageNotifier.updateNotification(context, true);
    }				
  }
		
  private void handleSentMessage(Intent intent) {
    long messageId = intent.getLongExtra("message_id", -1);
    long type      = intent.getLongExtra("type", -1);
		
    Log.w("SMSReceiverService", "Intent resultcode: " + intent.getIntExtra("ResultCode", 42));
    Log.w("SMSReceiverService", "Running sent callback: " + messageId + "," + type);
		
    if (intent.getIntExtra("ResultCode", -31337) == Activity.RESULT_OK)
      DatabaseFactory.getSmsDatabase(context).markAsSent(messageId, type);
    else
      DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);
  }
		
  public void process(MasterSecret masterSecret, Intent intent) {
    if (intent.getAction().equals(SendReceiveService.RECEIVE_SMS_ACTION))                 
      handleReceiveMessage(masterSecret, intent);
    else if (intent.getAction().equals(SendReceiveService.SENT_SMS_ACTION)) 
      handleSentMessage(intent);
  }
}
