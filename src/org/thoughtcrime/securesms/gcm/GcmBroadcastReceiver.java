package org.thoughtcrime.securesms.gcm;

import android.content.Context;

public class GcmBroadcastReceiver extends com.google.android.gcm.GCMBroadcastReceiver {

  @Override
  protected String getGCMIntentServiceClassName(Context context) {
    return "org.thoughtcrime.securesms.gcm.GcmIntentService";
  }

}