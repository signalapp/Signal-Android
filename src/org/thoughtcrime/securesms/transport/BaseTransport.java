package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.service.SmsDeliveryListener;

public abstract class BaseTransport {

  protected Intent constructSentIntent(Context context, long messageId, long type,
                                       boolean upgraded, boolean push)
  {
    Intent pending = new Intent(SendReceiveService.SENT_SMS_ACTION,
                                Uri.parse("custom://" + messageId + System.currentTimeMillis()),
                                context, SmsDeliveryListener.class);

    pending.putExtra("type", type);
    pending.putExtra("message_id", messageId);
    pending.putExtra("upgraded", upgraded);
    pending.putExtra("push", push);

    return pending;
  }

  protected Intent constructDeliveredIntent(Context context, long messageId, long type) {
    Intent pending = new Intent(SendReceiveService.DELIVERED_SMS_ACTION,
                                Uri.parse("custom://" + messageId + System.currentTimeMillis()),
                                context, SmsDeliveryListener.class);
    pending.putExtra("type", type);
    pending.putExtra("message_id", messageId);

    return pending;
  }
}
