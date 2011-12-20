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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.SecureSMS;
import org.thoughtcrime.securesms.crypto.MasterSecret;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Small service that stays running to keep a key cached in memory.
 * 
 * @author Moxie Marlinspike
 */

public class KeyCachingService extends Service {

  public static final int    NOTIFICATION_ID    = 1337;
  public static final int    SERVICE_RUNNING_ID = 4141;
	
  public  static final String KEY_PERMISSION           = "org.thoughtcrime.securesms.ACCESS_SECRETS";
  public  static final String NEW_KEY_EVENT            = "org.thoughtcrime.securesms.service.action.NEW_KEY_EVENT";
  public  static final String PASSPHRASE_EXPIRED_EVENT = "org.thoughtcrime.securesms.service.action.PASSPHRASE_EXPIRED_EVENT";
  public  static final String CLEAR_KEY_ACTION         = "org.thoughtcrime.securesms.service.action.CLEAR_KEY";
  public  static final String ACTIVITY_START_EVENT     = "org.thoughtcrime.securesms.service.action.ACTIVITY_START_EVENT";
  public  static final String ACTIVITY_STOP_EVENT      = "org.thoughtcrime.securesms.service.action.ACTIVITY_STOP_EVENT";
  public  static final String PREFERENCES_NAME         = "SecureSMS-Preferences";

  private static final Class[] mStartForegroundSignature = new Class[] {int.class, Notification.class};
  private static final Class[] mStopForegroundSignature = new Class[] {boolean.class};
    
  private PendingIntent pending;
  private NotificationManager notificationManager;
  private Method mStartForeground;
  private Method mStopForeground;
    
  private Object[] mStartForegroundArgs = new Object[2];
  private Object[] mStopForegroundArgs  = new Object[1];
  private int activitiesRunning         = 0;
  private final IBinder binder          = new KeyCachingBinder();
	
  private MasterSecret masterSecret;
	
  public KeyCachingService() {}

  public synchronized MasterSecret getMasterSecret() {
    return masterSecret;
  }
	
  public synchronized void setMasterSecret(MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
		
    foregroundService();
    broadcastNewSecret();
    startTimeoutIfAppropriate();
  }
	
  @Override
  public void onStart(Intent intent, int startId) {
    if (intent.getAction() != null && intent.getAction().equals(CLEAR_KEY_ACTION))
      handleClearKey();
    else if (intent.getAction() != null && intent.getAction().equals(ACTIVITY_START_EVENT))
      handleActivityStarted();
    else if (intent.getAction() != null && intent.getAction().equals(ACTIVITY_STOP_EVENT))
      handleActivityStopped();
    else if (intent.getAction() != null && intent.getAction().equals(PASSPHRASE_EXPIRED_EVENT))
      handlePassphraseExpired();
  }
	
  @Override
  public void onCreate() {
    pending             = PendingIntent.getService(this, 0, new Intent(PASSPHRASE_EXPIRED_EVENT, null, this, KeyCachingService.class), 0);
    notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

    try {
      mStartForeground = getClass().getMethod("startForeground", mStartForegroundSignature);
      mStopForeground  = getClass().getMethod("stopForeground", mStopForegroundSignature);
    } catch (NoSuchMethodException e) {
      // Running on an older platform.
      mStartForeground = mStopForeground = null;
    }        
  }
	
  @Override
  public void onDestroy() {
    Log.e("kcs", "KCS Is Being Destroyed!");
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
    stopForegroundCompat(SERVICE_RUNNING_ID);		
  }
	
  private void handlePassphraseExpired() {
    handleClearKey();
    Intent intent = new Intent(PASSPHRASE_EXPIRED_EVENT);
    intent.setPackage(getApplicationContext().getPackageName());
    
    sendBroadcast(intent, KEY_PERMISSION);
  }
	
  private void startTimeoutIfAppropriate() {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    boolean timeoutEnabled              = sharedPreferences.getBoolean(ApplicationPreferencesActivity.PASSPHRASE_TIMEOUT_PREF, false);
		
    if ((activitiesRunning == 0) && (this.masterSecret != null) && timeoutEnabled) {
      long timeoutMinutes = sharedPreferences.getInt(ApplicationPreferencesActivity.PASSPHRASE_TIMEOUT_INTERVAL_PREF, 60 * 5);
      long timeoutMillis  = timeoutMinutes * 60 * 1000;

      Log.w("KeyCachingService", "Starting timeout: " + timeoutMillis);
			
      AlarmManager alarmManager = (AlarmManager)this.getSystemService(ALARM_SERVICE);
      alarmManager.cancel(pending);
      alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + timeoutMillis, pending);
    }		
  }
	
  private void foregroundService() {
    Notification notification  = new Notification(R.drawable.icon, "TextSecure Passphrase Cached", System.currentTimeMillis());
    Intent intent              = new Intent(this, SecureSMS.class);
    PendingIntent launchIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
    notification.setLatestEventInfo(getApplicationContext(), "TextSecure Cached", "TextSecure Passphrase Cached", launchIntent);
		
    stopForegroundCompat(SERVICE_RUNNING_ID);
    startForegroundCompat(SERVICE_RUNNING_ID, notification);
  }
	
  private void broadcastNewSecret() {
    Log.w("service", "Broadcasting new secret...");
    Intent intent = new Intent(NEW_KEY_EVENT);
    intent.putExtra("master_secret", masterSecret);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);
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
  /**
   * This is a wrapper around the new startForeground method, using the older
   * APIs if it is not available.
   */
  private void startForegroundCompat(int id, Notification notification) {
    if (mStartForeground != null) {
      mStartForegroundArgs[0] = Integer.valueOf(id);
      mStartForegroundArgs[1] = notification;

      try {
        mStartForeground.invoke(this, mStartForegroundArgs);
      } catch (InvocationTargetException e) {
        Log.w("KeyCachingService", "Unable to invoke startForeground", e);
      } catch (IllegalAccessException e) {
        Log.w("KeyCachingService", "Unable to invoke startForeground", e);
      }

      return;
    }
        
    setForeground(true);
    notificationManager.notify(id, notification);
  }
    
  /**
   * This is a wrapper around the new stopForeground method, using the older
   * APIs if it is not available.
   */
  private void stopForegroundCompat(int id) {
    Log.w("KeyCachingService", "Calling stopForeground!");
    if (mStopForeground != null) {
      mStopForegroundArgs[0] = Boolean.TRUE;
      try {
        mStopForeground.invoke(this, mStopForegroundArgs);
      } catch (InvocationTargetException e) {
        Log.w("KeyCachingService", "Unable to invoke stopForeground", e);
      } catch (IllegalAccessException e) {
        Log.w("KeyCachingService", "Unable to invoke stopForeground", e);
      }
      return;
    }
        
    notificationManager.cancel(id);
    setForeground(false);
  }
}
