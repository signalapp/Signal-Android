package org.thoughtcrime.securesms.gcm;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;

/**
 * Works with {@link FcmFetchManager} to exists as a service that will keep the app process running in the background while we fetch messages.
 */
public class FcmFetchBackgroundService extends Service {

  private static final String TAG = Log.tag(FcmFetchBackgroundService.class);

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    Log.i(TAG, "onDestroy()");
  }

  @Override
  public @Nullable IBinder onBind(Intent intent) {
    return null;
  }
}

