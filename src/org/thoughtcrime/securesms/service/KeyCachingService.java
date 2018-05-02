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
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.DatabaseUpgradeActivity;
import org.thoughtcrime.securesms.DummyActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

/**
 * Small service that stays running to keep a key cached in memory.
 *
 * @author Moxie Marlinspike
 */

public class KeyCachingService extends Service {

  public static final int SERVICE_RUNNING_ID = 4141;

  public  static final String KEY_PERMISSION           = "org.thoughtcrime.securesms.ACCESS_SECRETS";
  public  static final String NEW_KEY_EVENT            = "org.thoughtcrime.securesms.service.action.NEW_KEY_EVENT";
  public  static final String CLEAR_KEY_EVENT          = "org.thoughtcrime.securesms.service.action.CLEAR_KEY_EVENT";
  private static final String PASSPHRASE_EXPIRED_EVENT = "org.thoughtcrime.securesms.service.action.PASSPHRASE_EXPIRED_EVENT";
  public  static final String CLEAR_KEY_ACTION         = "org.thoughtcrime.securesms.service.action.CLEAR_KEY";
  public  static final String DISABLE_ACTION           = "org.thoughtcrime.securesms.service.action.DISABLE";
  public  static final String ACTIVITY_START_EVENT     = "org.thoughtcrime.securesms.service.action.ACTIVITY_START_EVENT";
  public  static final String ACTIVITY_STOP_EVENT      = "org.thoughtcrime.securesms.service.action.ACTIVITY_STOP_EVENT";
  public  static final String LOCALE_CHANGE_EVENT      = "org.thoughtcrime.securesms.service.action.LOCALE_CHANGE_EVENT";

  private DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private PendingIntent pending;
  private int activitiesRunning = 0;
  private final IBinder binder  = new KeySetBinder();

  private static MasterSecret masterSecret;

  public KeyCachingService() {}

  public static synchronized boolean isLocked(Context context) {
    return getMasterSecret(context) == null;
  }

  public static synchronized @Nullable MasterSecret getMasterSecret(Context context) {
    if (masterSecret == null && (TextSecurePreferences.isPasswordDisabled(context) && !TextSecurePreferences.isScreenLockEnabled(context))) {
      try {
        MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(context, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
        Intent       intent       = new Intent(context, KeyCachingService.class);

        context.startService(intent);

        return masterSecret;
      } catch (InvalidPassphraseException e) {
        Log.w("KeyCachingService", e);
      }
    }

    return masterSecret;
  }

  @SuppressLint("StaticFieldLeak")
  public void setMasterSecret(final MasterSecret masterSecret) {
    synchronized (KeyCachingService.class) {
      KeyCachingService.masterSecret = masterSecret;

      foregroundService();
      broadcastNewSecret();
      startTimeoutIfAppropriate();
      
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          if (!DatabaseUpgradeActivity.isUpdate(KeyCachingService.this)) {
            MessageNotifier.updateNotification(KeyCachingService.this);
          }
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_NOT_STICKY;
    Log.w("KeyCachingService", "onStartCommand, " + intent.getAction());

    if (intent.getAction() != null) {
      switch (intent.getAction()) {
        case CLEAR_KEY_ACTION:         handleClearKey();        break;
        case ACTIVITY_START_EVENT:     handleActivityStarted(); break;
        case ACTIVITY_STOP_EVENT:      handleActivityStopped(); break;
        case PASSPHRASE_EXPIRED_EVENT: handleClearKey();        break;
        case DISABLE_ACTION:           handleDisableService();  break;
        case LOCALE_CHANGE_EVENT:      handleLocaleChanged();   break;
      }
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onCreate() {
    Log.w("KeyCachingService", "onCreate()");
    super.onCreate();
    this.pending = PendingIntent.getService(this, 0, new Intent(PASSPHRASE_EXPIRED_EVENT, null,
                                                                this, KeyCachingService.class), 0);

    if (TextSecurePreferences.isPasswordDisabled(this) && !TextSecurePreferences.isScreenLockEnabled(this)) {
      try {
        MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(this, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
        setMasterSecret(masterSecret);
      } catch (InvalidPassphraseException e) {
        Log.w("KeyCachingService", e);
      }
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.w("KeyCachingService", "KCS Is Being Destroyed!");
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

  private void handleActivityStarted() {
    Log.w("KeyCachingService", "Incrementing activity count...");

    AlarmManager alarmManager = ServiceUtil.getAlarmManager(this);
    alarmManager.cancel(pending);
    activitiesRunning++;
  }

  private void handleActivityStopped() {
    Log.w("KeyCachingService", "Decrementing activity count...");

    activitiesRunning--;
    startTimeoutIfAppropriate();
  }

  @SuppressLint("StaticFieldLeak")
  private void handleClearKey() {
    Log.w("KeyCachingService", "handleClearKey()");
    KeyCachingService.masterSecret = null;
    stopForeground(true);

    Intent intent = new Intent(CLEAR_KEY_EVENT);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        MessageNotifier.updateNotification(KeyCachingService.this);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleDisableService() {
    if (TextSecurePreferences.isPasswordDisabled(this) &&
        !TextSecurePreferences.isScreenLockEnabled(this))
    {
      stopForeground(true);
    }
  }

  private void handleLocaleChanged() {
    dynamicLanguage.updateServiceLocale(this);
    foregroundService();
  }

  private void startTimeoutIfAppropriate() {
    boolean timeoutEnabled = TextSecurePreferences.isPassphraseTimeoutEnabled(this);
    long    screenTimeout  = TextSecurePreferences.getScreenLockTimeout(this);

    if ((activitiesRunning == 0) && (KeyCachingService.masterSecret != null) &&
        (timeoutEnabled && !TextSecurePreferences.isPasswordDisabled(this)) ||
        (screenTimeout >= 60 && TextSecurePreferences.isScreenLockEnabled(this)))
    {
      long passphraseTimeoutMinutes = TextSecurePreferences.getPassphraseTimeoutInterval(this);
      long screenLockTimeoutSeconds = TextSecurePreferences.getScreenLockTimeout(this);

      long timeoutMillis;

      if (!TextSecurePreferences.isPasswordDisabled(this)) timeoutMillis = TimeUnit.MINUTES.toMillis(passphraseTimeoutMinutes);
      else                                                        timeoutMillis  = TimeUnit.SECONDS.toMillis(screenLockTimeoutSeconds);

      Log.w("KeyCachingService", "Starting timeout: " + timeoutMillis);

      AlarmManager alarmManager = ServiceUtil.getAlarmManager(this);
      alarmManager.cancel(pending);
      alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + timeoutMillis, pending);
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private void foregroundServiceModern() {
    Log.w("KeyCachingService", "foregrounding KCS");
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

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

  private void foregroundServiceICS() {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
    RemoteViews remoteViews            = new RemoteViews(getPackageName(), R.layout.key_caching_notification);

    remoteViews.setOnClickPendingIntent(R.id.lock_cache_icon, buildLockIntent());

    builder.setSmallIcon(R.drawable.icon_cached);
    builder.setContent(remoteViews);
    builder.setContentIntent(buildLaunchIntent());

    stopForeground(true);
    startForeground(SERVICE_RUNNING_ID, builder.build());
  }

  private void foregroundServiceLegacy() {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
    builder.setSmallIcon(R.drawable.icon_cached);
    builder.setWhen(System.currentTimeMillis());

    builder.setContentTitle(getString(R.string.KeyCachingService_passphrase_cached));
    builder.setContentText(getString(R.string.KeyCachingService_signal_passphrase_cached));
    builder.setContentIntent(buildLaunchIntent());

    stopForeground(true);
    startForeground(SERVICE_RUNNING_ID, builder.build());
  }

  private void foregroundService() {
    if (TextSecurePreferences.isPasswordDisabled(this) && !TextSecurePreferences.isScreenLockEnabled(this)) {
      stopForeground(true);
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      foregroundServiceModern();
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      foregroundServiceICS();
    } else {
      foregroundServiceLegacy();
    }
  }

  private void broadcastNewSecret() {
    Log.w("service", "Broadcasting new secret...");

    Intent intent = new Intent(NEW_KEY_EVENT);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);
  }

  private PendingIntent buildLockIntent() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(PASSPHRASE_EXPIRED_EVENT);
    return PendingIntent.getService(getApplicationContext(), 0, intent, 0);
  }

  private PendingIntent buildLaunchIntent() {
    Intent intent              = new Intent(this, ConversationListActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
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

  public static void registerPassphraseActivityStarted(Context activity) {
    Intent intent = new Intent(activity, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_START_EVENT);
    activity.startService(intent);
  }

  public static void registerPassphraseActivityStopped(Context activity) {
    Intent intent = new Intent(activity, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_STOP_EVENT);
    activity.startService(intent);
  }
}
