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

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;

import ws.com.google.android.mms.pdu.GenericPdu;
import ws.com.google.android.mms.pdu.NotificationInd;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduParser;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MmsReceiver {
	
  private final Context context;
	
  public MmsReceiver(Context context) {
    this.context = context;
  }
	
  private void scheduleDownload(NotificationInd pdu, long messageId) {
    Intent intent = new Intent(SendReceiveService.DOWNLOAD_MMS_ACTION, null, context, SendReceiveService.class);
    intent.putExtra("content_location", new String(pdu.getContentLocation()));
    intent.putExtra("message_id", messageId);
    intent.putExtra("transaction_id", pdu.getTransactionId());
    intent.putExtra("thread_id", DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId));

    context.startService(intent);
  }
	
  public void process(MasterSecret masterSecret, Intent intent) {
    byte[] mmsData   = intent.getByteArrayExtra("data");
    PduParser parser = new PduParser(mmsData);
    GenericPdu pdu   = parser.parse();

    if (pdu.getMessageType() == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
      MmsDatabase database;
        	
      if (masterSecret != null)
	database = DatabaseFactory.getEncryptingMmsDatabase(context, masterSecret);
      else
	database = DatabaseFactory.getMmsDatabase(context);
        	
      long messageId = database.insertMessageReceived((NotificationInd)pdu);
      MessageNotifier.updateNotification(context, true);
      scheduleDownload((NotificationInd)pdu, messageId);
      Log.w("MmsReceiverService", "Inserted received notification...");
    }		
  }

}
