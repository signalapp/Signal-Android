package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.service.SmsListener;

public abstract class BaseTransport {

  protected Intent constructSentIntent(Context context, long messageId, long type, boolean upgraded) {
    Intent pending = new Intent(SendReceiveService.SENT_SMS_ACTION,
                                Uri.parse("custom://" + messageId + System.currentTimeMillis()),
                                context, SmsListener.class);

    pending.putExtra("type", type);
    pending.putExtra("message_id", messageId);
    pending.putExtra("upgraded", upgraded);

    return pending;
  }

  protected Intent constructDeliveredIntent(Context context, long messageId, long type) {
    Intent pending = new Intent(SendReceiveService.DELIVERED_SMS_ACTION,
                                Uri.parse("custom://" + messageId + System.currentTimeMillis()),
                                context, SmsListener.class);
    pending.putExtra("type", type);
    pending.putExtra("message_id", messageId);

    return pending;
  }
}
