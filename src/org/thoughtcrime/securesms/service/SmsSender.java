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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.SendReceiveService.ToastHandler;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.transport.UniversalTransport;

public class SmsSender {

  private final Context context;
  private final ToastHandler toastHandler;

  public SmsSender(Context context, ToastHandler toastHandler) {
    this.context      = context;
    this.toastHandler = toastHandler;
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    if (SendReceiveService.SEND_SMS_ACTION.equals(intent.getAction())) {
      handleSendMessage(masterSecret, intent);
    } else if (SendReceiveService.SENT_SMS_ACTION.equals(intent.getAction())) {
      handleSentMessage(intent);
    } else if (SendReceiveService.DELIVERED_SMS_ACTION.equals(intent.getAction())) {
      handleDeliveredMessage(intent);
    }
  }

  private void handleSendMessage(MasterSecret masterSecret, Intent intent) {
    long messageId                      = intent.getLongExtra("message_id", -1);
    UniversalTransport transport        = new UniversalTransport(context, masterSecret);
    EncryptingSmsDatabase database      = DatabaseFactory.getEncryptingSmsDatabase(context);

    EncryptingSmsDatabase.Reader reader = null;
    SmsMessageRecord record;

    Log.w("SmsSender", "Sending message: " + messageId);

    try {
      if (messageId != -1) reader = database.getMessage(masterSecret, messageId);
      else                 reader = database.getOutgoingMessages(masterSecret);

      while (reader != null && (record = reader.getNext()) != null) {
        database.markAsSending(record.getId());
        transport.deliver(record);
      }
    } catch (UndeliverableMessageException ude) {
      Log.w("SmsSender", ude);
      DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  private void handleSentMessage(Intent intent) {
    long    messageId = intent.getLongExtra("message_id", -1);
    int     result    = intent.getIntExtra("ResultCode", -31337);
    boolean upgraded  = intent.getBooleanExtra("upgraded", false);

    Log.w("SMSReceiverService", "Intent resultcode: " + result);
    Log.w("SMSReceiverService", "Running sent callback: " + messageId);

    if (result == Activity.RESULT_OK) {
      DatabaseFactory.getSmsDatabase(context).markAsSent(messageId);

      if (upgraded) {
        DatabaseFactory.getSmsDatabase(context).markAsSecure(messageId);
      }

      unregisterForRadioChanges();
    } else if (result == SmsManager.RESULT_ERROR_NO_SERVICE || result == SmsManager.RESULT_ERROR_RADIO_OFF) {
      DatabaseFactory.getSmsDatabase(context).markAsOutbox(messageId);
      toastHandler
        .obtainMessage(0, context.getString(R.string.SmsReceiver_currently_unable_to_send_your_sms_message))
        .sendToTarget();
      registerForRadioChanges();
    } else {
      long threadId         = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageId);
      Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);

      DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);
      MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
      unregisterForRadioChanges();
    }
  }

  private void handleDeliveredMessage(Intent intent) {
    long messageId     = intent.getLongExtra("message_id", -1);
    byte[] pdu         = intent.getByteArrayExtra("pdu");
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
      Log.w("SmsSender", iae);
    }
  }
}
