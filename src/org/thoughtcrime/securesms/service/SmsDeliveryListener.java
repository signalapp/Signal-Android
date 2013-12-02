package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SmsDeliveryListener extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (SendReceiveService.SENT_SMS_ACTION.equals(intent.getAction())) {
      intent.putExtra("ResultCode", this.getResultCode());
      intent.setClass(context, SendReceiveService.class);
      context.startService(intent);
    } else if (SendReceiveService.DELIVERED_SMS_ACTION.equals(intent.getAction())) {
      intent.putExtra("ResultCode", this.getResultCode());
      intent.setClass(context, SendReceiveService.class);
      context.startService(intent);
    }
  }
}
