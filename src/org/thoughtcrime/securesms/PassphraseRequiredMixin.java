package org.thoughtcrime.securesms;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.shortcutBadger.ShortcutBadgeException;
import com.shortcutBadger.ShortcutBadger;

import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.service.KeyCachingService;


public class PassphraseRequiredMixin {

  private KeyCachingServiceConnection serviceConnection;
  private BroadcastReceiver clearKeyReceiver;
  private BroadcastReceiver newKeyReceiver;

  public void onCreate(Context context, PassphraseRequiredActivity activity) {
    initializeClearKeyReceiver(context, activity);
  }

  public void onResume(Context context, PassphraseRequiredActivity activity) {
    clearBadgeCount(context);
    initializeNewKeyReceiver(context, activity);
    initializeServiceConnection(context, activity);
    KeyCachingService.registerPassphraseActivityStarted(context);
  }

  private void clearBadgeCount(Context context) {
    Context appContext = context.getApplicationContext();
    int badgeCount = 0;
    try {
      ShortcutBadger.setBadge(appContext, badgeCount);
    } catch (ShortcutBadgeException e) {
      Log.w("Error setting message count badge",e);
    }
  }

  public void onPause(Context context, PassphraseRequiredActivity activity) {
    removeNewKeyReceiver(context);
    removeServiceConnection(context);
    KeyCachingService.registerPassphraseActivityStopped(context);
  }

  public void onDestroy(Context context, PassphraseRequiredActivity activity) {
    removeClearKeyReceiver(context);
  }

  private void initializeClearKeyReceiver(Context context, final PassphraseRequiredActivity activity) {
    this.clearKeyReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        activity.onMasterSecretCleared();
      }
    };

    IntentFilter filter = new IntentFilter(KeyCachingService.CLEAR_KEY_EVENT);
    context.registerReceiver(clearKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null);
  }

  private void initializeNewKeyReceiver(Context context, final PassphraseRequiredActivity activity) {
    this.newKeyReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        activity.onNewMasterSecret((MasterSecret)intent.getParcelableExtra("master_secret"));
      }
    };

    IntentFilter filter = new IntentFilter(KeyCachingService.NEW_KEY_EVENT);
    context.registerReceiver(newKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null);
  }

  private void initializeServiceConnection(Context context, PassphraseRequiredActivity activity) {
    Intent cachingIntent = new Intent(context, KeyCachingService.class);
    context.startService(cachingIntent);

    this.serviceConnection = new KeyCachingServiceConnection(activity);

    Intent bindIntent = new Intent(context, KeyCachingService.class);
    context.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
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
    if (this.serviceConnection != null && this.serviceConnection.isBound()) {
      context.unbindService(this.serviceConnection);
    }
  }

  private static class KeyCachingServiceConnection implements ServiceConnection {
    private final PassphraseRequiredActivity activity;

    private boolean isBound;

    public KeyCachingServiceConnection(PassphraseRequiredActivity activity) {
      this.activity = activity;
      this.isBound  = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      KeyCachingService keyCachingService  = ((KeyCachingService.KeyCachingBinder)service).getService();
      MasterSecret masterSecret            = keyCachingService.getMasterSecret();
      this.isBound                         = true;

      if (masterSecret == null) {
        activity.onMasterSecretCleared();
      } else {
        activity.onNewMasterSecret(masterSecret);
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      this.isBound  = false;
    }

    public boolean isBound() {
      return this.isBound;
    }
  }

}
