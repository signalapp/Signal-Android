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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.SessionCipher;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.protocol.KeyExchangeWirePrefix;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.protocol.SecureMessageWirePrefix;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.SendReceiveService.ToastHandler;
import org.thoughtcrime.securesms.sms.MultipartMessageHandler;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.thoughtcrime.securesms.util.InvalidMessageException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SmsSender {

  private final MultipartMessageHandler multipartMessageHandler = new MultipartMessageHandler();
  private final Set<Long>               pendingMessages         = new HashSet<Long>();

  private final Context context;
  private final ToastHandler toastHandler;

  public SmsSender(Context context, ToastHandler toastHandler) {
    this.context      = context;
    this.toastHandler = toastHandler;
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    if (intent.getAction().equals(SendReceiveService.SEND_SMS_ACTION)) {
      handleSendMessage(masterSecret, intent);
    } else if (intent.getAction().equals(SendReceiveService.SENT_SMS_ACTION)) {
      handleSentMessage(intent);
    } else if (intent.getAction().equals(SendReceiveService.DELIVERED_SMS_ACTION)) {
      handleDeliveredMessage(intent);
    }
  }

  private void handleSendMessage(MasterSecret masterSecret, Intent intent) {
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

          if (!pendingMessages.contains(messageId)) {
            Log.w("SMSSenderService", "Actually delivering: " + messageId);
            pendingMessages.add(messageId);
            deliverTextMessage(address, messageText, messageId, type);
          }
        } while (c.moveToNext());
      }
    } finally {
      if (c != null)
        c.close();
    }
  }

  private void handleSentMessage(Intent intent) {
    long messageId = intent.getLongExtra("message_id", -1);
    long type      = intent.getLongExtra("type", -1);
    int result     = intent.getIntExtra("ResultCode", -31337);

    Log.w("SMSReceiverService", "Intent resultcode: " + result);
    Log.w("SMSReceiverService", "Running sent callback: " + messageId + "," + type);

    if (result == Activity.RESULT_OK) {
      DatabaseFactory.getSmsDatabase(context).markAsSent(messageId, type);
      unregisterForRadioChanges();
    } else if (result == SmsManager.RESULT_ERROR_NO_SERVICE || result == SmsManager.RESULT_ERROR_RADIO_OFF) {
      toastHandler
        .obtainMessage(0, context.getString(R.string.SmsReceiver_currently_unable_to_send_your_sms_message))
        .sendToTarget();
      registerForRadioChanges();
    } else {
      long threadId         = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageId);
      Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(context, threadId);

      DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);
      MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
      unregisterForRadioChanges();
    }

    pendingMessages.remove(messageId);
  }

  private void handleDeliveredMessage(Intent intent) {
    long messageId     = intent.getLongExtra("message_id", -1);
    long type          = intent.getLongExtra("type", -1);
    byte[] pdu         = intent.getByteArrayExtra("pdu");
    String format      = intent.getStringExtra("format");
    SmsMessage message = SmsMessage.createFromPdu(pdu);

    if (message == null) {
        return;
    }

    DatabaseFactory.getSmsDatabase(context).markStatus(messageId, message.getStatus());
  }

  private void registerForRadioChanges() {
    unregisterForRadioChanges();

    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(SystemStateListener.ACTION_SERVICE_STATE);
    context.registerReceiver(SystemStateListener.getInstance(), intentFilter);
  }

  private void unregisterForRadioChanges() {
    try {
      context.unregisterReceiver(SystemStateListener.getInstance());
    } catch (IllegalArgumentException iae) {

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

  private ArrayList<PendingIntent> constructDeliveredIntents(long messageId, long type, ArrayList<String> messages) {
    if (!PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(ApplicationPreferencesActivity.SMS_DELIVERY_REPORT_PREF, false))
    {
      return null;
    }

    ArrayList<PendingIntent> deliveredIntents = new ArrayList<PendingIntent>(messages.size());

    for (int i=0;i<messages.size();i++) {
      Intent pending = new Intent(SendReceiveService.DELIVERED_SMS_ACTION, Uri.parse("custom://" + messageId + System.currentTimeMillis()), context, SmsListener.class);
      pending.putExtra("type", type);
      pending.putExtra("message_id", messageId);
      deliveredIntents.add(PendingIntent.getBroadcast(context, 0, pending, 0));
    }

    return deliveredIntents;
  }

  private void deliverGSMTransportTextMessage(String recipient, String text, long messageId, long type) {
    ArrayList<String> messages                = SmsManager.getDefault().divideMessage(text);
    ArrayList<PendingIntent> sentIntents      = constructSentIntents(messageId, type, messages);
    ArrayList<PendingIntent> deliveredIntents = constructDeliveredIntents(messageId, type, messages);

    // XXX moxie@thoughtcrime.org 1/7/11 -- There's apparently a bug where for some unknown recipients
    // and messages, this will throw an NPE.  I have no idea why, so I'm just catching it and marking
    // the message as a failure.  That way at least it doesn't repeatedly crash every time you start
    // the app.
    try {
      SmsManager.getDefault().sendMultipartTextMessage(recipient, null, messages, sentIntents, deliveredIntents);
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

    ArrayList<String> messages                = multipartMessageHandler.divideMessage(recipient, text, prefix);
    ArrayList<PendingIntent> sentIntents      = constructSentIntents(messageId, type, messages);
    ArrayList<PendingIntent> deliveredIntents = constructDeliveredIntents(messageId, type, messages);

    for (int i=0;i<messages.size();i++) {
      // XXX moxie@thoughtcrime.org 1/7/11 -- There's apparently a bug where for some unknown recipients
      // and messages, this will throw an NPE.  I have no idea why, so I'm just catching it and marking
      // the message as a failure.  That way at least it doesn't repeatedly crash every time you start
      // the app.
      try {
        SmsManager.getDefault().sendTextMessage(recipient, null, messages.get(i), sentIntents.get(i),
                                                deliveredIntents == null ? null : deliveredIntents.get(i));
      } catch (NullPointerException npe) {
        Log.w("SmsSender", npe);
        DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);
      } catch (IllegalArgumentException iae) {
        Log.w("SmsSender", iae);
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
      SessionCipher cipher = new SessionCipher(context, masterSecret, new Recipient(null, address, null, null), new SmsTransportDetails());
      return new String(cipher.encryptMessage(body.getBytes()));
    }
  }

}
