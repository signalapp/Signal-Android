/**
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

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.DatabaseUpgradeActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RoutingActivity;
import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

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

  private PendingIntent pending;
  private int activitiesRunning = 0;
  private final IBinder binder  = new KeyCachingBinder();

  private MasterSecret masterSecret;

  public KeyCachingService() {}

  public synchronized MasterSecret getMasterSecret() {
    return masterSecret;
  }

  public synchronized void setMasterSecret(final MasterSecret masterSecret) {
    this.masterSecret = masterSecret;

    foregroundService();
    broadcastNewSecret();
    startTimeoutIfAppropriate();

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        if (!DatabaseUpgradeActivity.isUpdate(KeyCachingService.this)) {
          DecryptingQueue.schedulePendingDecrypts(KeyCachingService.this, masterSecret);
          MessageNotifier.updateNotification(KeyCachingService.this, masterSecret);
        }
        return null;
      }
    }.execute();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_NOT_STICKY;

    if (intent.getAction() != null && intent.getAction().equals(CLEAR_KEY_ACTION))
      handleClearKey();
    else if (intent.getAction() != null && intent.getAction().equals(ACTIVITY_START_EVENT))
      handleActivityStarted();
    else if (intent.getAction() != null && intent.getAction().equals(ACTIVITY_STOP_EVENT))
      handleActivityStopped();
    else if (intent.getAction() != null && intent.getAction().equals(PASSPHRASE_EXPIRED_EVENT))
      handleClearKey();
    else if (intent.getAction() != null && intent.getAction().equals(DISABLE_ACTION))
      handleDisableService();

    return START_NOT_STICKY;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    this.pending = PendingIntent.getService(this, 0, new Intent(PASSPHRASE_EXPIRED_EVENT, null,
                                                                this, KeyCachingService.class), 0);

    if (TextSecurePreferences.isPasswordDisabled(this)) {
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

  private void handleActivityStarted() {
    Log.w("KeyCachingService", "Incrementing activity count...");

    AlarmManager alarmManager = (AlarmManager)this.getSystemService(ALARM_SERVICE);
    alarmManager.cancel(pending);
    activitiesRunning++;
  }

  private void handleActivityStopped() {
    Log.w("KeyCachingService", "Decrementing activity count...");

    activitiesRunning--;
    startTimeoutIfAppropriate();
  }

  private void handleClearKey() {
    this.masterSecret = null;
    stopForeground(true);

    Intent intent = new Intent(CLEAR_KEY_EVENT);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        MessageNotifier.updateNotification(KeyCachingService.this, null);
        return null;
      }
    }.execute();
  }

  private void handleDisableService() {
    if (TextSecurePreferences.isPasswordDisabled(this))
      stopForeground(true);
  }

  private void startTimeoutIfAppropriate() {
    boolean timeoutEnabled = TextSecurePreferences.isPassphraseTimeoutEnabled(this);

    if ((activitiesRunning == 0) && (this.masterSecret != null) && timeoutEnabled && !TextSecurePreferences.isPasswordDisabled(this)) {
      long timeoutMinutes = TextSecurePreferences.getPassphraseTimeoutInterval(this);
      long timeoutMillis  = timeoutMinutes * 60 * 1000;

      Log.w("KeyCachingService", "Starting timeout: " + timeoutMillis);

      AlarmManager alarmManager = (AlarmManager)this.getSystemService(ALARM_SERVICE);
      alarmManager.cancel(pending);
      alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + timeoutMillis, pending);
    }
  }

  private void foregroundServiceModern() {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

    builder.setContentTitle(getString(R.string.KeyCachingService_passphrase_cached));
    builder.setContentText(getString(R.string.KeyCachingService_textsecure_passphrase_cached));
    builder.setSmallIcon(R.drawable.icon_cached);
    builder.setWhen(0);
    builder.setPriority(Notification.PRIORITY_LOW);

    builder.addAction(R.drawable.ic_menu_lock_holo_dark, getString(R.string.KeyCachingService_lock), buildLockIntent());
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
    Notification notification  = new Notification(R.drawable.icon_cached,
                                                  getString(R.string.KeyCachingService_textsecure_passphrase_cached),
                                                  System.currentTimeMillis());
    notification.setLatestEventInfo(getApplicationContext(),
                                    getString(R.string.KeyCachingService_passphrase_cached),
                                    getString(R.string.KeyCachingService_textsecure_passphrase_cached),
                                    buildLaunchIntent());
    notification.tickerText = null;

    stopForeground(true);
    startForeground(SERVICE_RUNNING_ID, notification);
  }

  private void foregroundService() {
    if (TextSecurePreferences.isPasswordDisabled(this)) {
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
    intent.putExtra("master_secret", masterSecret);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);
  }

  private PendingIntent buildLockIntent() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(PASSPHRASE_EXPIRED_EVENT);
    PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, 0);
    return pendingIntent;
  }

  private PendingIntent buildLaunchIntent() {
    Intent intent              = new Intent(this, RoutingActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent launchIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
    return launchIntent;
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return binder;
  }

  public class KeyCachingBinder extends Binder {
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
