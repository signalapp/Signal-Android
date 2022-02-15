package org.thoughtcrime.securesms.payments.backup.phrase;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ServiceUtil;

public class ClearClipboardAlarmReceiver extends BroadcastReceiver {
  private static final String TAG = Log.tag(ClearClipboardAlarmReceiver.class);

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "onReceive: clearing clipboard");

    ClipboardManager clipboardManager = ServiceUtil.getClipboardManager(context);
    clipboardManager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), " "));
  }
}
