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

import org.thoughtcrime.securesms.protocol.WirePrefix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsListener extends BroadcastReceiver {

  private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";

  private boolean isExemption(SmsMessage message, String messageBody) {
    // Sprint Visual Voicemail
    return 
      message.getOriginatingAddress().length() < 7 && 
      (messageBody.startsWith("//ANDROID:") || messageBody.startsWith("//Android:") || 
       messageBody.startsWith("//android:") || messageBody.startsWith("//BREW:"));
  }
	
  private SmsMessage getSmsMessageFromIntent(Intent intent) {
    Bundle bundle             = intent.getExtras();
    Object[] pdus             = (Object[])bundle.get("pdus");

    if (pdus == null || pdus.length == 0)
      return null;
		
    return SmsMessage.createFromPdu((byte[])pdus[0]);
  }
	
  private String getSmsMessageBodyFromIntent(Intent intent) {
    Bundle bundle             = intent.getExtras();
    Object[] pdus             = (Object[])bundle.get("pdus");
    StringBuilder bodyBuilder = new StringBuilder();
			
    if (pdus == null)
      return null;
		
    for (int i=0;i<pdus.length;i++)
      bodyBuilder.append(SmsMessage.createFromPdu((byte[])pdus[i]).getDisplayMessageBody());

    return bodyBuilder.toString();
  }
	
  private boolean isRelevent(Context context, Intent intent) {
    SmsMessage message = getSmsMessageFromIntent(intent);
    String messageBody = getSmsMessageBodyFromIntent(intent);

    if (message == null && messageBody == null)
      return false;
		
    if (isExemption(message, messageBody))
      return false;
			
    if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_all_sms", true))
      return true;		

    return WirePrefix.isEncryptedMessage(messageBody) || WirePrefix.isKeyExchange(messageBody);
  }
	
  @Override
    public void onReceive(Context context, Intent intent) {
    Log.w("SMSListener", "Got SMS broadcast...");
		
    if (intent.getAction().equals(SMS_RECEIVED_ACTION) && isRelevent(context, intent)) {
      intent.setAction(SendReceiveService.RECEIVE_SMS_ACTION);
      intent.putExtra("ResultCode", this.getResultCode());
      intent.setClass(context, SendReceiveService.class);
      context.startService(intent);

      abortBroadcast();
    } else if (intent.getAction().equals(SendReceiveService.SENT_SMS_ACTION)) {
      intent.putExtra("ResultCode", this.getResultCode());
      intent.setClass(context, SendReceiveService.class);
      context.startService(intent);
    }
  }
}
