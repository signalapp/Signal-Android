/*
 * Copyright (C) 2011 Whisper Systems
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

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.DatabaseUpgradeActivity;
import org.thoughtcrime.securesms.DummyActivity;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.loki.activities.HomeActivity;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.session.libsession.utilities.ServiceUtil;
import org.session.libsession.utilities.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

import network.loki.messenger.R;

/**
 * Small service that stays running to keep a key cached in memory.
 *
 * @author Moxie Marlinspike
 */
//TODO AC: This service does only serve one purpose now - to track the screen lock state and handle the timer.
// We need to refactor it and cleanup from all the old Signal code.
public class KeyCachingService extends Service {

  private static final String TAG = KeyCachingService.class.getSimpleName();

  public static final int SERVICE_RUNNING_ID = 4141;

  public  static final String KEY_PERMISSION           = "network.loki.messenger.ACCESS_SESSION_SECRETS";
  public  static final String CLEAR_KEY_EVENT          = "org.thoughtcrime.securesms.service.action.CLEAR_KEY_EVENT";
  public  static final String LOCK_TOGGLED_EVENT       = "org.thoughtcrime.securesms.service.action.LOCK_ENABLED_EVENT";
  private static final String PASSPHRASE_EXPIRED_EVENT = "org.thoughtcrime.securesms.service.action.PASSPHRASE_EXPIRED_EVENT";
  public  static final String CLEAR_KEY_ACTION         = "org.thoughtcrime.securesms.service.action.CLEAR_KEY";

  private final IBinder binder  = new KeySetBinder();

  // AC: This is a temporal drop off replacement for the refactoring time being.
  // This field only indicates if the app was unlocked or not (null means locked).
  private static Object masterSecret = null;

  /**
   * A temporal utility method to quickly call {@link KeyCachingService#setMasterSecret(Object)}
   * without explicitly binding to the service.
   */
  public static void setMasterSecret(Context context, Object masterSecret) {
    // Start and bind to the KeyCachingService instance.
    Intent bindIntent = new Intent(context, KeyCachingService.class);
    context.startService(bindIntent);
    context.bindService(bindIntent, new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName name, IBinder binder) {
        KeyCachingService service = ((KeySetBinder) binder).getService();
        service.setMasterSecret(masterSecret);
        context.unbindService(this);
      }
      @Override
      public void onServiceDisconnected(ComponentName name) {

      }
    }, Context.BIND_AUTO_CREATE);

  }

  public KeyCachingService() {}

  public static synchronized boolean isLocked(Context context) {
    boolean enabled = !TextSecurePreferences.isPasswordDisabled(context) || TextSecurePreferences.isScreenLockEnabled(context);
    return getMasterSecret(context) == null && enabled;
  }

  public static void onAppForegrounded(@NonNull Context context) {
    ServiceUtil.getAlarmManager(context).cancel(buildExpirationPendingIntent(context));
  }

  public static void onAppBackgrounded(@NonNull Context context) {
    startTimeoutIfAppropriate(context);
  }

  public static synchronized @Nullable Object getMasterSecret(Context context) {
    return masterSecret;
  }

  @SuppressLint("StaticFieldLeak")
  public void setMasterSecret(final Object masterSecret) {
    synchronized (KeyCachingService.class) {
      KeyCachingService.masterSecret = masterSecret;

      foregroundService();
      
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          if (!DatabaseUpgradeActivity.isUpdate(KeyCachingService.this)) {
            ApplicationContext.getInstance(KeyCachingService.this).messageNotifier.updateNotification(KeyCachingService.this);
          }
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_NOT_STICKY;
    Log.d(TAG, "onStartCommand, " + intent.getAction());

    if (intent.getAction() != null) {
      switch (intent.getAction()) {
        case CLEAR_KEY_ACTION:         handleClearKey();        break;
        case PASSPHRASE_EXPIRED_EVENT: handleClearKey();        break;
        case LOCK_TOGGLED_EVENT:       handleLockToggled();     break;
      }
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onCreate() {
    Log.i(TAG, "onCreate()");
    super.onCreate();

    if (TextSecurePreferences.isPasswordDisabled(this) && !TextSecurePreferences.isScreenLockEnabled(this)) {
        setMasterSecret(new Object());
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.w(TAG, "KCS Is Being Destroyed!");
    handleClearKey();
  }

  /**
   * Workaround for Android bug:
   * https://code.google.com/p/android/issues/detail?id=53313
   */
  @Override
  public void onTaskRemoved(Intent rootIntent) {
    Intent intent = new Intent(this, DummyActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleClearKey() {
    Log.i(TAG, "handleClearKey()");
    KeyCachingService.masterSecret = null;
    stopForeground(true);

    Intent intent = new Intent(CLEAR_KEY_EVENT);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        ApplicationContext.getInstance(KeyCachingService.this).messageNotifier.updateNotification(KeyCachingService.this);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleLockToggled() {
    stopForeground(true);
    setMasterSecret(masterSecret);
  }

  private static void startTimeoutIfAppropriate(@NonNull Context context) {
    boolean appVisible       = ApplicationContext.getInstance(context).isAppVisible();
    boolean secretSet        = KeyCachingService.masterSecret != null;

    boolean timeoutEnabled   = TextSecurePreferences.isPassphraseTimeoutEnabled(context);
    boolean passLockActive   = timeoutEnabled && !TextSecurePreferences.isPasswordDisabled(context);

    long    screenTimeout    = TextSecurePreferences.getScreenLockTimeout(context);
    boolean screenLockActive = screenTimeout >= 0 && TextSecurePreferences.isScreenLockEnabled(context);

    if (!appVisible && secretSet && (passLockActive || screenLockActive)) {
      long passphraseTimeoutMinutes = TextSecurePreferences.getPassphraseTimeoutInterval(context);
      long screenLockTimeoutSeconds = TextSecurePreferences.getScreenLockTimeout(context);

      long timeoutMillis;

      if (!TextSecurePreferences.isPasswordDisabled(context)) timeoutMillis = TimeUnit.MINUTES.toMillis(passphraseTimeoutMinutes);
      else                                                    timeoutMillis = TimeUnit.SECONDS.toMillis(screenLockTimeoutSeconds);

      Log.i(TAG, "Starting timeout: " + timeoutMillis);

      AlarmManager  alarmManager     = ServiceUtil.getAlarmManager(context);
      PendingIntent expirationIntent = buildExpirationPendingIntent(context);

      alarmManager.cancel(expirationIntent);
      alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + timeoutMillis, expirationIntent);
    }
  }

  private void foregroundService() {
    if (TextSecurePreferences.isPasswordDisabled(this) && !TextSecurePreferences.isScreenLockEnabled(this)) {
      stopForeground(true);
      return;
    }

    Log.i(TAG, "foregrounding KCS");
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationChannels.LOCKED_STATUS);

    builder.setContentTitle(getString(R.string.KeyCachingService_passphrase_cached));
    builder.setContentText(getString(R.string.KeyCachingService_signal_passphrase_cached));
    builder.setSmallIcon(R.drawable.icon_cached);
    builder.setWhen(0);
    builder.setPriority(Notification.PRIORITY_MIN);

    builder.addAction(R.drawable.ic_menu_lock_dark, getString(R.string.KeyCachingService_lock), buildLockIntent());
    builder.setContentIntent(buildLaunchIntent());

    stopForeground(true);
    startForeground(SERVICE_RUNNING_ID, builder.build());
  }

  private PendingIntent buildLockIntent() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(PASSPHRASE_EXPIRED_EVENT);
    return PendingIntent.getService(getApplicationContext(), 0, intent, 0);
  }

  private PendingIntent buildLaunchIntent() {
    Intent intent              = new Intent(this, HomeActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
  }

  private static PendingIntent buildExpirationPendingIntent(@NonNull Context context) {
    Intent expirationIntent = new Intent(PASSPHRASE_EXPIRED_EVENT, null, context, KeyCachingService.class);
    return PendingIntent.getService(context, 0, expirationIntent, 0);
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return binder;
  }

  public class KeySetBinder extends Binder {
    public KeyCachingService getService() {
      return KeyCachingService.this;
    }
  }
}
