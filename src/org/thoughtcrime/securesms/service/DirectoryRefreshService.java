/**
 * Copyright (C) 2013-2014 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
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
