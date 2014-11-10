package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import org.thoughtcrime.securesms.util.DirectoryHelper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DirectoryRefreshService extends Service {

  public  static final String REFRESH_ACTION = "org.whispersystems.whisperpush.REFRESH_ACTION";

  private static final Executor executor = Executors.newSingleThreadExecutor();

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    if (REFRESH_ACTION.equals(intent.getAction())) {
      handleRefreshAction();
    }
    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void handleRefreshAction() {
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Directory Refresh");
    wakeLock.acquire();

    executor.execute(new RefreshRunnable(wakeLock));
  }

  private class RefreshRunnable implements Runnable {
    private final PowerManager.WakeLock wakeLock;
    private final Context context;

    public RefreshRunnable(PowerManager.WakeLock wakeLock) {
      this.wakeLock = wakeLock;
      this.context  = DirectoryRefreshService.this.getApplicationContext();
    }

    public void run() {
      try {
        Log.w("DirectoryRefreshService", "Refreshing directory...");

        DirectoryHelper.refreshDirectory(context);

        Log.w("DirectoryRefreshService", "Directory refresh complete...");
      } finally {
        if (wakeLock != null && wakeLock.isHeld())
          wakeLock.release();
      }
    }
  }
}
