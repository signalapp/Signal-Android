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

import java.util.ArrayList;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.SessionCipher;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.protocol.KeyExchangeWirePrefix;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.protocol.SecureMessageWirePrefix;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MultipartMessageHandler;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.thoughtcrime.securesms.util.InvalidMessageException;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.SmsManager;
import android.util.Log;

public class SmsSender {

  private final MultipartMessageHandler multipartMessageHandler = new MultipartMessageHandler();
	
  private final Context context;
	
  public SmsSender(Context context) {
    this.context = context;
  }
		
  public void process(MasterSecret masterSecret, Intent intent) {
    MasterCipher masterCipher = new MasterCipher(masterSecret);
    long messageId            = intent.getLongExtra("message_id", -1);
    Cursor c                  = null;

    Log.w("SMSSenderService", "Processing outgoing message: " + messageId);

    try {
      if (messageId == -1) c = DatabaseFactory.getSmsDatabase(context).getOutgoingMessages();
      else                 c = DatabaseFactory.getSmsDatabase(context).getMessage(messageId);
			
      if (c != null && c.moveToFirst()) {
	do {
	  messageId          = c.getLong(c.getColumnIndexOrThrow(SmsDatabase.ID));
	  String body        = c.getString(c.getColumnIndexOrThrow(SmsDatabase.BODY));
	  String address     = c.getString(c.getColumnIndexOrThrow(SmsDatabase.ADDRESS));				
	  String messageText = getClearTextBody(masterCipher, body);
	  long type          = c.getLong(c.getColumnIndexOrThrow(SmsDatabase.TYPE));
		
	  if (!SmsDatabase.Types.isPendingMessageType(type))
	    continue;
					
	  if (isSecureMessage(type))
	    messageText    = getAsymmetricEncrypt(masterSecret, messageText, address);
					
	  Log.w("SMSSenderService", "Actually delivering: " + messageId);

	  deliverTextMessage(address, messageText, messageId, type);
	} while (c.moveToNext());
      }			
    } finally {
      if (c != null)
	c.close();
    }
  }
		
  private String getClearTextBody(MasterCipher masterCipher, String body) {
    if (body.startsWith(Prefix.SYMMETRIC_ENCRYPT)) {
      try {
	return masterCipher.decryptBody(body.substring(Prefix.SYMMETRIC_ENCRYPT.length()));
      } catch (InvalidMessageException e) {
	return "Error decrypting message.";
      }
    } else {
      return body;
    }
  }
	
  private ArrayList<PendingIntent> constructSentIntents(long messageId, long type, ArrayList<String> messages) {
    ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>(messages.size());

    for (int i=0;i<messages.size();i++) {
      Intent pending = new Intent(SendReceiveService.SENT_SMS_ACTION, Uri.parse("custom://" + messageId + System.currentTimeMillis()), context, SmsListener.class);
      pending.putExtra("type", type);
      pending.putExtra("message_id", messageId);
      sentIntents.add(PendingIntent.getBroadcast(context, 0, pending, 0));
    }
		
    return sentIntents;
  }
	
  private void deliverGSMTransportTextMessage(String recipient, String text, long messageId, long type) {
    ArrayList<String> messages = SmsManager.getDefault().divideMessage(text);
    ArrayList<PendingIntent> sentIntents = constructSentIntents(messageId, type, messages);
    // XXX moxie@thoughtcrime.org 1/7/11 -- There's apparently a bug where for some unknown recipients
    // and messages, this will throw an NPE.  I have no idea why, so I'm just catching it and marking
    // the message as a failure.  That way at least it doesn't repeatedly crash every time you start
    // the app.
    try {
      SmsManager.getDefault().sendMultipartTextMessage(recipient, null, messages, sentIntents, null);
    } catch (NullPointerException npe) {
      Log.w("SmsSender", npe);
      DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);
    }
  }

  private void deliverSecureTransportTextMessage(String recipient, String text, long messageId, long type) {
    WirePrefix prefix;
		
    if (isSecureMessage(type)) {
      prefix = new SecureMessageWirePrefix();
      text   = text.substring(Prefix.ASYMMETRIC_ENCRYPT.length());
    } else {
      prefix = new KeyExchangeWirePrefix();
      text   = text.substring(Prefix.KEY_EXCHANGE.length());
    }
		
    if (!multipartMessageHandler.isManualTransport(text)) {
      deliverGSMTransportTextMessage(recipient, prefix.calculatePrefix(text) + text, messageId, type);
      return;
    }
		
    ArrayList<String> messages = multipartMessageHandler.divideMessage(recipient, text, prefix);
    ArrayList<PendingIntent> sentIntents = constructSentIntents(messageId, type, messages);
    for (int i=0;i<messages.size();i++) {
      // XXX moxie@thoughtcrime.org 1/7/11 -- There's apparently a bug where for some unknown recipients
      // and messages, this will throw an NPE.  I have no idea why, so I'm just catching it and marking
      // the message as a failure.  That way at least it doesn't repeatedly crash every time you start
      // the app.
      try {
	SmsManager.getDefault().sendTextMessage(recipient, null, messages.get(i), sentIntents.get(i), null);
      } catch (NullPointerException npe) {
	Log.w("SmsSender", npe);
	DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);
      }
    }
  }
		
  private void deliverTextMessage(String recipient, String text, long messageId, long type) {
    if (!isSecureMessage(type) && !isKeyExchange(text))
      deliverGSMTransportTextMessage(recipient, text, messageId, type);
    else
      deliverSecureTransportTextMessage(recipient, text, messageId, type);		
  }
	
  private boolean isSecureMessage(long type) {
    return type == SmsDatabase.Types.ENCRYPTING_TYPE;
  }
	
  private boolean isKeyExchange(String messageText) {
    return messageText.startsWith(Prefix.KEY_EXCHANGE);
  }
	
  private String getAsymmetricEncrypt(MasterSecret masterSecret, String body, String address) {
    synchronized (SessionCipher.CIPHER_LOCK) {
      SessionCipher cipher = new SessionCipher(context, masterSecret, new Recipient(null, address, null), new SmsTransportDetails());
      return new String(cipher.encryptMessage(body.getBytes()));
    }
  }
	
}
