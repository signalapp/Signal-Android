/**
 * Copyright (C) 2013-2014 Open WhisperSystems
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
