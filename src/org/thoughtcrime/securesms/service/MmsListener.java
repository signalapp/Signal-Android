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
import org.thoughtcrime.securesms.protocol.WirePrefix;

import ws.com.google.android.mms.pdu.GenericPdu;
import ws.com.google.android.mms.pdu.NotificationInd;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduParser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

public class MmsListener extends BroadcastReceiver {

  private boolean isRelevent(Context context, Intent intent) {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.DONUT)
      return false;
		
    if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ApplicationPreferencesActivity.ALL_MMS_PERF, true))
      return true;
		
    byte[] mmsData   = intent.getByteArrayExtra("data");
    PduParser parser = new PduParser(mmsData);
    GenericPdu pdu   = parser.parse();
        
    if (pdu.getMessageType() != PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND)
      return false;
        
    NotificationInd notificationPdu = (NotificationInd)pdu;
        
    if (notificationPdu.getSubject() == null)
      return false;
        
    return WirePrefix.isEncryptedMmsSubject(notificationPdu.getSubject().getString());
  }
	
  @Override
    public void onReceive(Context context, Intent intent) {
    Log.w("MmsListener", "Got MMS broadcast...");
		
    if (isRelevent(context, intent)) {
      intent.setAction(SendReceiveService.RECEIVE_MMS_ACTION);
      intent.putExtra("ResultCode", this.getResultCode());
      intent.setClass(context, SendReceiveService.class);
			
      context.startService(intent);
      abortBroadcast();
    }
  }

	
	
}
