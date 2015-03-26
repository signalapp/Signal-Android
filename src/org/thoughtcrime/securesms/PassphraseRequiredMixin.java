package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import android.view.WindowManager;

import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.MessageRetrievalService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;


public class PassphraseRequiredMixin {
  private static final String TAG = PassphraseRequiredMixin.class.getSimpleName();

  private BroadcastReceiver clearKeyReceiver;

  public <T extends Activity & MasterSecretListener> void onCreate(T activity) {
    initializeClearKeyReceiver(activity);
  }

  public <T extends Activity & MasterSecretListener> void onResume(T activity) {
    initializeScreenshotSecurity(activity);
    KeyCachingService.registerPassphraseActivityStarted(activity);
    MessageRetrievalService.registerActivityStarted(activity);
  }

  public <T extends Activity & MasterSecretListener> void onPause(T activity) {
    KeyCachingService.registerPassphraseActivityStopped(activity);
    MessageRetrievalService.registerActivityStopped(activity);
  }

  public <T extends Activity & MasterSecretListener> void onDestroy(T activity) {
    removeClearKeyReceiver(activity);
  }

  private <T extends Activity & MasterSecretListener> void initializeScreenshotSecurity(T activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      if (TextSecurePreferences.isScreenSecurityEnabled(activity)) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
      } else {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
      }
    }
  }

  private <T extends Activity & MasterSecretListener> void initializeClearKeyReceiver(final T activity) {
    Log.w(TAG, "initializeClearKeyReceiver()");
    this.clearKeyReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        Log.w(TAG, "onReceive() for clear key event");
        activity.onMasterSecretCleared();
      }
    };

    IntentFilter filter = new IntentFilter(KeyCachingService.CLEAR_KEY_EVENT);

    activity.registerReceiver(clearKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null);
  }

  private void removeClearKeyReceiver(Context context) {
    if (clearKeyReceiver != null) {
      context.unregisterReceiver(clearKeyReceiver);
      clearKeyReceiver = null;
    }
  }
}
