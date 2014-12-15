package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import android.view.WindowManager;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.MessageRetrievalService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;


public class PassphraseRequiredMixin {

  private BroadcastReceiver clearKeyReceiver;
  private BroadcastReceiver newKeyReceiver;

  public <T extends Activity & PassphraseRequiredActivity> void onCreate(T activity) {
    initializeClearKeyReceiver(activity);
  }

  public <T extends Activity & PassphraseRequiredActivity> void onResume(T activity) {
    initializeScreenshotSecurity(activity);
    initializeNewKeyReceiver(activity);
    KeyCachingService.registerPassphraseActivityStarted(activity);
    MessageRetrievalService.registerActivityStarted(activity);
  }

  public <T extends Activity & PassphraseRequiredActivity> void onPause(T activity) {
    removeNewKeyReceiver(activity);
    KeyCachingService.registerPassphraseActivityStopped(activity);
    MessageRetrievalService.registerActivityStopped(activity);
  }

  public <T extends Activity & PassphraseRequiredActivity> void onDestroy(T activity) {
    removeClearKeyReceiver(activity);
  }

  private <T extends Activity & PassphraseRequiredActivity> void initializeScreenshotSecurity(T activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      if (TextSecurePreferences.isScreenSecurityEnabled(activity)) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
      } else {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
      }
    }
  }

  private <T extends Activity & PassphraseRequiredActivity> void initializeClearKeyReceiver(final T activity) {
    Log.w("PassphraseRequiredMixin", "initializeClearKeyReceiver()");
    this.clearKeyReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        Log.w("PassphraseRequiredMixin", "onReceive() for clear key event");
        activity.onMasterSecretCleared();
      }
    };

    IntentFilter filter = new IntentFilter(KeyCachingService.CLEAR_KEY_EVENT);

    activity.registerReceiver(clearKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null);
  }

  private <T extends Activity & PassphraseRequiredActivity> void initializeNewKeyReceiver(final T activity) {
    this.newKeyReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        activity.onNewMasterSecret(KeyCachingService.getMasterSecret(activity));
      }
    };

    IntentFilter filter = new IntentFilter(KeyCachingService.NEW_KEY_EVENT);
    activity.registerReceiver(newKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null);
  }

  private void removeClearKeyReceiver(Context context) {
    if (clearKeyReceiver != null) {
      context.unregisterReceiver(clearKeyReceiver);
      clearKeyReceiver = null;
    }
  }

  private void removeNewKeyReceiver(Context context) {
    if (newKeyReceiver != null) {
      context.unregisterReceiver(newKeyReceiver);
      newKeyReceiver = null;
    }
  }
}
