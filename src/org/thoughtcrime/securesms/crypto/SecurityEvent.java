package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.service.KeyCachingService;

/**
 * This class processes key exchange interactions.
 *
 * @author Moxie Marlinspike
 */

public class SecurityEvent {

  public static final String SECURITY_UPDATE_EVENT = "org.thoughtcrime.securesms.KEY_EXCHANGE_UPDATE";

  public static void broadcastSecurityUpdateEvent(Context context) {
    broadcastSecurityUpdateEvent(context, -2);
  }

  public static void broadcastSecurityUpdateEvent(Context context, long threadId) {
    Intent intent = new Intent(SECURITY_UPDATE_EVENT);
    intent.putExtra("thread_id", threadId);
    intent.setPackage(context.getPackageName());
    context.sendBroadcast(intent, KeyCachingService.KEY_PERMISSION);
  }

}
