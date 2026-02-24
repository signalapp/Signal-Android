package org.signal.core.util;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;

import org.signal.core.util.logging.Log;

public class ClearClipboardAlarmReceiver extends BroadcastReceiver {
  private static final String TAG = Log.tag(ClearClipboardAlarmReceiver.class);

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "onReceive: clearing clipboard");

    ClipboardManager clipboardManager = ContextCompat.getSystemService(context, ClipboardManager.class);
    if (Build.VERSION.SDK_INT >= 28) {
      clipboardManager.clearPrimaryClip();
    } else {
      String appName = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
      clipboardManager.setPrimaryClip(ClipData.newPlainText(appName, " "));
    }
  }
}
