package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.view.WindowManager;

import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.service.KeyCachingService;


public class PassphraseRequiredMixin {

  private KeyCachingServiceConnection serviceConnection;
  private BroadcastReceiver clearKeyReceiver;
  private BroadcastReceiver newKeyReceiver;

  public <T extends Activity & PassphraseRequiredActivity> void onCreate(T activity) {
    initializeClearKeyReceiver(activity);
  }

  public <T extends Activity & PassphraseRequiredActivity> void onResume(T activity) {
    initializeScreenshotSecurity(activity);
    initializeNewKeyReceiver(activity);
    initializeServiceConnection(activity);
    KeyCachingService.registerPassphraseActivityStarted(activity);
  }

  public <T extends Activity & PassphraseRequiredActivity> void onPause(T activity) {
    removeNewKeyReceiver(activity);
    removeServiceConnection(activity);
    KeyCachingService.registerPassphraseActivityStopped(activity);
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
    this.clearKeyReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
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
        activity.onNewMasterSecret((MasterSecret)intent.getParcelableExtra("master_secret"));
      }
    };

    IntentFilter filter = new IntentFilter(KeyCachingService.NEW_KEY_EVENT);
    activity.registerReceiver(newKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null);
  }

  private <T extends Activity & PassphraseRequiredActivity> void initializeServiceConnection(T activity) {
    Intent cachingIntent = new Intent(activity, KeyCachingService.class);
    activity.startService(cachingIntent);

    this.serviceConnection = new KeyCachingServiceConnection(activity);

    Intent bindIntent = new Intent(activity, KeyCachingService.class);
    activity.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
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

  private void removeServiceConnection(Context context) {
    if (this.serviceConnection != null) {
      context.unbindService(this.serviceConnection);
    }
  }

  private static class KeyCachingServiceConnection implements ServiceConnection {
    private final PassphraseRequiredActivity activity;

    public KeyCachingServiceConnection(PassphraseRequiredActivity activity) {
      this.activity = activity;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      KeyCachingService keyCachingService  = ((KeyCachingService.KeyCachingBinder)service).getService();
      MasterSecret masterSecret            = keyCachingService.getMasterSecret();

      if (masterSecret == null) {
        activity.onMasterSecretCleared();
      } else {
        activity.onNewMasterSecret(masterSecret);
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }

  }

}
