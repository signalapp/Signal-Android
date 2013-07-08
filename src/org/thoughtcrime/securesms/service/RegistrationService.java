package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.gcm.GcmIntentService;
import org.thoughtcrime.securesms.gcm.GcmRegistrationTimeoutException;
import org.thoughtcrime.securesms.gcm.PushServiceSocket;
import org.thoughtcrime.securesms.gcm.RateLimitException;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The RegisterationService handles the process of PushService registration and verification.
 * If it receives an intent with a REGISTER_NUMBER_ACTION, it does the following through
 * an executor:
 *
 * 1) Generate secrets.
 * 2) Register the specified number and those secrets with the server.
 * 3) Wait for a challenge SMS.
 * 4) Verify the challenge with the server.
 * 5) Start the GCM registration process.
 * 6) Retrieve the current directory.
 *
 * The RegistrationService broadcasts its state throughout this process, and also makes its
 * state available through service binding.  This enables a View to display progress.
 *
 * @author Moxie Marlinspike
 *
 */

public class RegistrationService extends Service {

  public static final String REGISTER_NUMBER_ACTION = "org.thoughtcrime.securesms.RegistrationService.REGISTER_NUMBER";
  public static final String VOICE_REQUESTED_ACTION = "org.thoughtcrime.securesms.RegistrationService.VOICE_REQUESTED";
  public static final String VOICE_REGISTER_ACTION  = "org.thoughtcrime.securesms.RegistrationService.VOICE_REGISTER";

  public static final String NOTIFICATION_TITLE     = "org.thoughtcrime.securesms.NOTIFICATION_TITLE";
  public static final String NOTIFICATION_TEXT      = "org.thoughtcrime.securesms.NOTIFICATION_TEXT";
  public static final String CHALLENGE_EVENT        = "org.thoughtcrime.securesms.CHALLENGE_EVENT";
  public static final String REGISTRATION_EVENT     = "org.thoughtcrime.securesms.REGISTRATION_EVENT";
  public static final String GCM_REGISTRATION_EVENT = "org.thoughtcrime.securesms.GCM_REGISTRATION_EVENT";

  public static final String CHALLENGE_EXTRA        = "CAAChallenge";
  public static final String GCM_REGISTRATION_ID    = "GCMRegistrationId";

  private static final long REGISTRATION_TIMEOUT_MILLIS = 120000;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Binder          binder   = new RegistrationServiceBinder();

  private volatile RegistrationState registrationState = new RegistrationState(RegistrationState.STATE_IDLE);

  private volatile Handler                 registrationStateHandler;
  private volatile ChallengeReceiver       challengeReceiver;
  private volatile GcmRegistrationReceiver gcmRegistrationReceiver;
  private          String                  challenge;
  private          String                  gcmRegistrationId;
  private          long                    verificationStartTime;

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    if (intent != null) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          if      (intent.getAction().equals(REGISTER_NUMBER_ACTION)) handleRegistrationIntent(intent);
          else if (intent.getAction().equals(VOICE_REQUESTED_ACTION)) handleVoiceRequestedIntent(intent);
          else if (intent.getAction().equals(VOICE_REGISTER_ACTION))  handleVoiceRegisterIntent(intent);
        }
      });
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    executor.shutdown();
    shutdown();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  public void shutdown() {
    shutdownChallengeListener();
    markAsVerifying(false);
    registrationState = new RegistrationState(RegistrationState.STATE_IDLE);
  }

  public synchronized int getSecondsRemaining() {
    long millisPassed;

    if (verificationStartTime == 0) millisPassed = 0;
    else                            millisPassed = System.currentTimeMillis() - verificationStartTime;

    return Math.max((int)(REGISTRATION_TIMEOUT_MILLIS - millisPassed) / 1000, 0);
  }

  public RegistrationState getRegistrationState() {
    return registrationState;
  }

  private void initializeChallengeListener() {
    this.challenge      = null;
    challengeReceiver = new ChallengeReceiver();
    IntentFilter filter = new IntentFilter(CHALLENGE_EVENT);
    registerReceiver(challengeReceiver, filter);
  }

  private void initializeGcmRegistrationListener() {
    this.gcmRegistrationId = null;
    gcmRegistrationReceiver = new GcmRegistrationReceiver();
    IntentFilter filter = new IntentFilter(GCM_REGISTRATION_EVENT);
    registerReceiver(gcmRegistrationReceiver, filter);
  }

  private synchronized void shutdownChallengeListener() {
    if (challengeReceiver != null) {
      unregisterReceiver(challengeReceiver);
      challengeReceiver = null;
    }
  }

  private synchronized void shutdownGcmRegistrationListener() {
    if (gcmRegistrationReceiver != null) {
      unregisterReceiver(gcmRegistrationReceiver);
      gcmRegistrationReceiver = null;
    }
  }

  private void handleVoiceRequestedIntent(Intent intent) {
    setState(new RegistrationState(RegistrationState.STATE_VOICE_REQUESTED,
                                   intent.getStringExtra("e164number"),
                                   intent.getStringExtra("password")));
  }

  private void handleVoiceRegisterIntent(Intent intent) {
    markAsVerifying(true);

    String number   = intent.getStringExtra("e164number");
    String password = intent.getStringExtra("password");

    try {
      initializeGcmRegistrationListener();

      PushServiceSocket socket = new PushServiceSocket(this, number, password);

      setState(new RegistrationState(RegistrationState.STATE_GCM_REGISTERING, number));
      GCMRegistrar.register(this, GcmIntentService.GCM_SENDER_ID);
      String gcmRegistrationId = waitForGcmRegistrationId();

      socket.registerGcmId(gcmRegistrationId);
      socket.retrieveDirectory(this);

      markAsVerified(number, password);

      setState(new RegistrationState(RegistrationState.STATE_COMPLETE, number));
      broadcastComplete(true);
    } catch (UnsupportedOperationException uoe) {
      Log.w("RegistrationService", uoe);
      setState(new RegistrationState(RegistrationState.STATE_GCM_UNSUPPORTED, number));
      broadcastComplete(false);
    } catch (IOException e) {
      Log.w("RegistrationService", e);
      setState(new RegistrationState(RegistrationState.STATE_NETWORK_ERROR, number));
      broadcastComplete(false);
    } catch (GcmRegistrationTimeoutException e) {
      Log.w("RegistrationService", e);
      setState(new RegistrationState(RegistrationState.STATE_GCM_TIMEOUT));
      broadcastComplete(false);
    } catch (RateLimitException e) {
      Log.w("RegistrationService", e);
      setState(new RegistrationState(RegistrationState.STATE_NETWORK_ERROR));
      broadcastComplete(false);
    } finally {
      shutdownGcmRegistrationListener();
    }
  }

  private void handleRegistrationIntent(Intent intent) {
    markAsVerifying(true);

    String number = intent.getStringExtra("e164number");

    try {
      String password = Util.getSecret(18);
      initializeChallengeListener();
      initializeGcmRegistrationListener();

      setState(new RegistrationState(RegistrationState.STATE_CONNECTING, number));
      PushServiceSocket socket = new PushServiceSocket(this, number, password);
      socket.createAccount(false);

      setState(new RegistrationState(RegistrationState.STATE_VERIFYING, number));
      String challenge = waitForChallenge();
      socket.verifyAccount(challenge);

      setState(new RegistrationState(RegistrationState.STATE_GCM_REGISTERING, number));
      GCMRegistrar.register(this, GcmIntentService.GCM_SENDER_ID);
      String gcmRegistrationId = waitForGcmRegistrationId();

      socket.registerGcmId(gcmRegistrationId);
      socket.retrieveDirectory(this);

      markAsVerified(number, password);

      setState(new RegistrationState(RegistrationState.STATE_COMPLETE, number));
      broadcastComplete(true);
    } catch (UnsupportedOperationException uoe) {
      Log.w("RegistrationService", uoe);
      setState(new RegistrationState(RegistrationState.STATE_GCM_UNSUPPORTED, number));
      broadcastComplete(false);
    } catch (AccountVerificationTimeoutException avte) {
      Log.w("RegistrationService", avte);
      setState(new RegistrationState(RegistrationState.STATE_TIMEOUT, number));
      broadcastComplete(false);
    } catch (IOException e) {
      Log.w("RegistrationService", e);
      setState(new RegistrationState(RegistrationState.STATE_NETWORK_ERROR, number));
      broadcastComplete(false);
    } catch (GcmRegistrationTimeoutException e) {
      Log.w("RegistrationService", e);
      setState(new RegistrationState(RegistrationState.STATE_GCM_TIMEOUT));
      broadcastComplete(false);
    } catch (RateLimitException e) {
      Log.w("RegistrationService", e);
      setState(new RegistrationState(RegistrationState.STATE_NETWORK_ERROR));
      broadcastComplete(false);
    } finally {
      shutdownChallengeListener();
      shutdownGcmRegistrationListener();
    }
  }

  private synchronized String waitForChallenge() throws AccountVerificationTimeoutException {
    this.verificationStartTime = System.currentTimeMillis();

    if (this.challenge == null) {
      try {
        wait(REGISTRATION_TIMEOUT_MILLIS);
      } catch (InterruptedException e) {
        throw new IllegalArgumentException(e);
      }
    }

    if (this.challenge == null)
      throw new AccountVerificationTimeoutException();

    return this.challenge;
  }

  private synchronized String waitForGcmRegistrationId() throws GcmRegistrationTimeoutException {
    if (this.gcmRegistrationId == null) {
      try {
        wait(10 * 60 * 1000);
      } catch (InterruptedException e) {
        throw new IllegalArgumentException(e);
      }
    }

    if (this.gcmRegistrationId == null)
      throw new GcmRegistrationTimeoutException();

    return this.gcmRegistrationId;
  }

  private synchronized void challengeReceived(String challenge) {
    this.challenge = challenge;
    notifyAll();
  }

  private synchronized void gcmRegistrationReceived(String gcmRegistrationId) {
    this.gcmRegistrationId = gcmRegistrationId;
    notifyAll();
  }

  private void markAsVerifying(boolean verifying) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    Editor editor                 = preferences.edit();

    editor.putBoolean(ApplicationPreferencesActivity.VERIFYING_STATE_PREF, verifying);
    editor.putBoolean(ApplicationPreferencesActivity.REGISTERED_GCM_PREF, false);
    editor.commit();
  }

  private void markAsVerified(String number, String password) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    Editor editor                 = preferences.edit();

    editor.putBoolean(ApplicationPreferencesActivity.VERIFYING_STATE_PREF, false);
    editor.putBoolean(ApplicationPreferencesActivity.REGISTERED_GCM_PREF, true);
    editor.putString(ApplicationPreferencesActivity.LOCAL_NUMBER_PREF, number);
    editor.putString(ApplicationPreferencesActivity.GCM_PASSWORD_PREF, password);
    editor.commit();
  }

  private void setState(RegistrationState state) {
    this.registrationState = state;

    if (registrationStateHandler != null) {
      registrationStateHandler.obtainMessage(state.state, state).sendToTarget();
    }
  }

  private void broadcastComplete(boolean success) {
    Intent intent = new Intent();
    intent.setAction(REGISTRATION_EVENT);

    if (success) {
      intent.putExtra(NOTIFICATION_TITLE, "Registration Complete");
      intent.putExtra(NOTIFICATION_TEXT, "TextSecure registration has successfully completed.");
    } else {
      intent.putExtra(NOTIFICATION_TITLE, "Registration Error");
      intent.putExtra(NOTIFICATION_TEXT, "TextSecure registration has encountered a problem.");
    }

    this.sendOrderedBroadcast(intent, null);
  }

  public void setRegistrationStateHandler(Handler registrationStateHandler) {
    this.registrationStateHandler = registrationStateHandler;
  }

  public class RegistrationServiceBinder extends Binder {
    public RegistrationService getService() {
      return RegistrationService.this;
    }
  }

  private class GcmRegistrationReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
      Log.w("RegistrationService", "Got gcm registration broadcast...");
      gcmRegistrationReceived(intent.getStringExtra(GCM_REGISTRATION_ID));
    }
  }

  private class ChallengeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w("RegistrationService", "Got a challenge broadcast...");
      challengeReceived(intent.getStringExtra(CHALLENGE_EXTRA));
    }
  }

  public static class RegistrationState {

    public static final int STATE_IDLE                 =  0;
    public static final int STATE_CONNECTING           =  1;
    public static final int STATE_VERIFYING            =  2;
    public static final int STATE_TIMER                =  3;
    public static final int STATE_COMPLETE             =  4;
    public static final int STATE_TIMEOUT              =  5;
    public static final int STATE_NETWORK_ERROR        =  6;

    public static final int STATE_GCM_UNSUPPORTED      =  8;
    public static final int STATE_GCM_REGISTERING      =  9;
    public static final int STATE_GCM_TIMEOUT          = 10;

    public static final int STATE_VOICE_REQUESTED      = 12;

    public final int    state;
    public final String number;
    public final String password;

    public RegistrationState(int state) {
      this(state, null);
    }

    public RegistrationState(int state, String number) {
      this(state, number, null);
    }

    public RegistrationState(int state, String number, String password) {
      this.state    = state;
      this.number   = number;
      this.password = password;
    }
  }
}
