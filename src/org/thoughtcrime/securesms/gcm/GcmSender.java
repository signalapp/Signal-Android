package org.thoughtcrime.securesms.gcm;

import android.app.PendingIntent;

public class GcmSender {

  private static final GcmSender instance = new GcmSender();

  public static GcmSender getDefault() {
    return instance;
  }

  public void sendTextMessage(String recipient, String text,
                              PendingIntent sentIntent,
                              PendingIntent deliveredIntent)
  {

  }

}
