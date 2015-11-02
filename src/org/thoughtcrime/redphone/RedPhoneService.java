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

package org.thoughtcrime.redphone;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.redphone.audio.IncomingRinger;
import org.thoughtcrime.redphone.audio.OutgoingRinger;
import org.thoughtcrime.redphone.call.CallManager;
import org.thoughtcrime.redphone.call.CallStateListener;
import org.thoughtcrime.redphone.call.InitiatingCallManager;
import org.thoughtcrime.redphone.call.LockManager;
import org.thoughtcrime.redphone.call.ResponderCallManager;
import org.thoughtcrime.redphone.crypto.zrtp.SASInfo;
import org.thoughtcrime.redphone.pstn.CallStateView;
import org.thoughtcrime.redphone.pstn.IncomingPstnCallListener;
import org.thoughtcrime.redphone.signaling.OtpCounterProvider;
import org.thoughtcrime.redphone.signaling.SessionDescriptor;
import org.thoughtcrime.redphone.signaling.SignalingException;
import org.thoughtcrime.redphone.signaling.SignalingSocket;
import org.thoughtcrime.redphone.ui.NotificationBarManager;
import org.thoughtcrime.redphone.util.UncaughtExceptionHandlerManager;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.events.RedPhoneEvent;
import org.thoughtcrime.securesms.events.RedPhoneEvent.Type;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import de.greenrobot.event.EventBus;

/**
 * The major entry point for all of the heavy lifting associated with
 * setting up, tearing down, or managing calls.  The service spins up
 * either from a broadcast listener that has detected an incoming call,
 * or from a UI element that wants to initiate an outgoing call.
 *
 * @author Moxie Marlinspike
 *
 */
public class RedPhoneService extends Service implements CallStateListener, CallStateView {

  private static final String TAG = RedPhoneService.class.getSimpleName();

  private static final int STATE_IDLE      = 0;
  private static final int STATE_RINGING   = 2;
  private static final int STATE_DIALING   = 3;
  private static final int STATE_ANSWERING = 4;
  private static final int STATE_CONNECTED = 5;

  public static final String EXTRA_REMOTE_NUMBER      = "remote_number";
  public static final String EXTRA_SESSION_DESCRIPTOR = "session_descriptor";
  public static final String EXTRA_MUTE               = "mute_value";

  public static final String ACTION_INCOMING_CALL = "org.thoughtcrime.redphone.RedPhoneService.INCOMING_CALL";
  public static final String ACTION_OUTGOING_CALL = "org.thoughtcrime.redphone.RedPhoneService.OUTGOING_CALL";
  public static final String ACTION_ANSWER_CALL   = "org.thoughtcrime.redphone.RedPhoneService.ANSWER_CALL";
  public static final String ACTION_DENY_CALL     = "org.thoughtcrime.redphone.RedPhoneService.DENY_CALL";
  public static final String ACTION_HANGUP_CALL   = "org.thoughtcrime.redphone.RedPhoneService.HANGUP";
  public static final String ACTION_SET_MUTE      = "org.thoughtcrime.redphone.RedPhoneService.SET_MUTE";

  private final Handler serviceHandler       = new Handler();

  private OutgoingRinger outgoingRinger;
  private IncomingRinger incomingRinger;

  private int                             state;
  private byte[]                          zid;
  private String                          remoteNumber;
  private CallManager                     currentCallManager;
  private LockManager                     lockManager;
  private UncaughtExceptionHandlerManager uncaughtExceptionHandlerManager;

  private IncomingPstnCallListener pstnCallListener;

  @Override
  public void onCreate() {
    super.onCreate();

    initializeResources();
    initializeRingers();
    initializePstnCallListener();
    registerUncaughtExceptionHandler();
  }

  @Override
  public void onStart(Intent intent, int startId) {
    if (intent == null) return;
    new Thread(new IntentRunnable(intent)).start();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    unregisterReceiver(pstnCallListener);
    NotificationBarManager.setCallEnded(this);
    uncaughtExceptionHandlerManager.unregister();
  }

  private synchronized void onIntentReceived(Intent intent) {
    Log.w(TAG, "Received Intent: " + intent.getAction());

    if      (intent.getAction().equals(ACTION_INCOMING_CALL) && isBusy()) handleBusyCall(intent);
    else if (intent.getAction().equals(ACTION_INCOMING_CALL))             handleIncomingCall(intent);
    else if (intent.getAction().equals(ACTION_OUTGOING_CALL) && isIdle()) handleOutgoingCall(intent);
    else if (intent.getAction().equals(ACTION_ANSWER_CALL))               handleAnswerCall(intent);
    else if (intent.getAction().equals(ACTION_DENY_CALL))                 handleDenyCall(intent);
    else if (intent.getAction().equals(ACTION_HANGUP_CALL))               handleHangupCall(intent);
    else if (intent.getAction().equals(ACTION_SET_MUTE))                  handleSetMute(intent);
  }

  ///// Initializers

  private void initializeRingers() {
    this.outgoingRinger = new OutgoingRinger(this);
    this.incomingRinger = new IncomingRinger(this);
  }

  private void initializePstnCallListener() {
    pstnCallListener = new IncomingPstnCallListener(this);
    registerReceiver(pstnCallListener, new IntentFilter("android.intent.action.PHONE_STATE"));
  }

  private void initializeResources() {
    this.state            = STATE_IDLE;
    this.zid              = getZID();
    this.lockManager      = new LockManager(this);
  }

  private void registerUncaughtExceptionHandler() {
    uncaughtExceptionHandlerManager = new UncaughtExceptionHandlerManager();
    uncaughtExceptionHandlerManager.registerHandler(new ProximityLockRelease(lockManager));
  }

  /// Intent Handlers

  private void handleIncomingCall(Intent intent) {
    String            localNumber = TextSecurePreferences.getLocalNumber(this);
    String            password    = TextSecurePreferences.getPushServerPassword(this);
    SessionDescriptor session     = intent.getParcelableExtra(EXTRA_SESSION_DESCRIPTOR);

    remoteNumber = intent.getStringExtra(EXTRA_REMOTE_NUMBER);
    state        = STATE_RINGING;

    lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
    this.currentCallManager = new ResponderCallManager(this, this, remoteNumber, localNumber,
                                                       password, session, zid);
    this.currentCallManager.start();
  }

  private void handleOutgoingCall(Intent intent) {
    String localNumber = TextSecurePreferences.getLocalNumber(this);
    String password    = TextSecurePreferences.getPushServerPassword(this);

    remoteNumber = intent.getStringExtra(EXTRA_REMOTE_NUMBER);

    if (remoteNumber == null || remoteNumber.length() == 0)
      return;

    sendMessage(Type.OUTGOING_CALL, getRecipient(), null);

    state = STATE_DIALING;
    lockManager.updatePhoneState(LockManager.PhoneState.INTERACTIVE);
    this.currentCallManager = new InitiatingCallManager(this, this, localNumber, password,
                                                        remoteNumber, zid);
    this.currentCallManager.start();

    NotificationBarManager.setCallInProgress(this);
    DatabaseFactory.getSmsDatabase(this).insertOutgoingCall(remoteNumber);

  }

  private void handleBusyCall(Intent intent) {
    String            localNumber = TextSecurePreferences.getLocalNumber(this);
    String            password    = TextSecurePreferences.getPushServerPassword(this);
    SessionDescriptor session     = intent.getParcelableExtra(EXTRA_SESSION_DESCRIPTOR);

    if (currentCallManager != null && session.equals(currentCallManager.getSessionDescriptor())) {
      Log.w(TAG, "Duplicate incoming call signal, ignoring...");
      return;
    }

    handleMissedCall(intent.getStringExtra(EXTRA_REMOTE_NUMBER));

    try {
      SignalingSocket signalingSocket = new SignalingSocket(this, session.getFullServerName(),
                                                            31337,
                                                            localNumber, password,
                                                            OtpCounterProvider.getInstance());

      signalingSocket.setBusy(session.sessionId);
      signalingSocket.close();
    } catch (SignalingException e) {
      Log.w(TAG, e);
    }
  }

  private void handleMissedCall(String remoteNumber) {
    DatabaseFactory.getSmsDatabase(this).insertMissedCall(remoteNumber);
    MessageNotifier.updateNotification(this, KeyCachingService.getMasterSecret(this));
  }

  private void handleAnswerCall(Intent intent) {
    state = STATE_ANSWERING;
    incomingRinger.stop();
    DatabaseFactory.getSmsDatabase(this).insertReceivedCall(remoteNumber);
    if (currentCallManager != null) {
      ((ResponderCallManager)this.currentCallManager).answer(true);
    }
  }

  private void handleDenyCall(Intent intent) {
    state = STATE_IDLE;
    incomingRinger.stop();
    DatabaseFactory.getSmsDatabase(this).insertMissedCall(remoteNumber);
    if(currentCallManager != null) {
      ((ResponderCallManager)this.currentCallManager).answer(false);
    }
    this.terminate();
  }

  private void handleHangupCall(Intent intent) {
    this.terminate();
  }

  private void handleSetMute(Intent intent) {
    if(currentCallManager != null) {
      currentCallManager.setMute(intent.getBooleanExtra(EXTRA_MUTE, false));
    }
  }

  /// Helper Methods

  private boolean isBusy() {
    TelephonyManager telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
    return ((currentCallManager != null && state != STATE_IDLE) ||
             telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE);
  }

  private boolean isIdle() {
    return state == STATE_IDLE;
  }

  private void shutdownAudio() {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    am.setMode(AudioManager.MODE_NORMAL);
  }

  public int getState() {
    return state;
  }

  public @NonNull Recipient getRecipient() {
    if (!TextUtils.isEmpty(remoteNumber)) {
      //noinspection ConstantConditions
      return RecipientFactory.getRecipientsFromString(this, remoteNumber, true)
                             .getPrimaryRecipient();
    } else {
      return Recipient.getUnknownRecipient();
    }
  }

  private byte[] getZID() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    if (preferences.contains("ZID")) {
      try {
        return Base64.decode(preferences.getString("ZID", null));
      } catch (IOException e) {
        return setZID();
      }
    } else {
      return setZID();
    }
  }

  private byte[] setZID() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    try {
      byte[] zid        = new byte[12];
      SecureRandom.getInstance("SHA1PRNG").nextBytes(zid);
      String encodedZid = Base64.encodeBytes(zid);

      preferences.edit().putString("ZID", encodedZid).commit();

      return zid;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private void startCallCardActivity() {
    Intent activityIntent = new Intent();
    activityIntent.setClass(this, RedPhone.class);
    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(activityIntent);
  }

  private synchronized void terminate() {
    Log.w(TAG, "termination stack", new Exception());
    lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
    NotificationBarManager.setCallEnded(this);

    incomingRinger.stop();
    outgoingRinger.stop();

    if (currentCallManager != null) {
      currentCallManager.terminate();
      currentCallManager = null;
    }

    shutdownAudio();

    state = STATE_IDLE;
    lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
  }

  ///////// CallStateListener Implementation

  public void notifyCallStale() {
    Log.w(TAG, "Got a stale call, probably an old SMS...");
    handleMissedCall(remoteNumber);
    this.terminate();
  }

  public void notifyCallFresh() {
    Log.w(TAG, "Good call, time to ring and display call card...");
    sendMessage(Type.INCOMING_CALL, getRecipient(), null);

    lockManager.updatePhoneState(LockManager.PhoneState.INTERACTIVE);

    startCallCardActivity();
    incomingRinger.start();

    NotificationBarManager.setCallInProgress(this);
  }

  public void notifyBusy() {
    Log.w("RedPhoneService", "Got busy signal from responder!");
    sendMessage(Type.CALL_BUSY, getRecipient(), null);

    outgoingRinger.playBusy();
    serviceHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        RedPhoneService.this.terminate();
      }
    }, RedPhone.BUSY_SIGNAL_DELAY_FINISH);
  }

  public void notifyCallRinging() {
    outgoingRinger.playRing();
    sendMessage(Type.CALL_RINGING, getRecipient(), null);
  }

  public void notifyCallConnected(SASInfo sas) {
    outgoingRinger.playComplete();
    lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
    state = STATE_CONNECTED;
    sendMessage(Type.CALL_CONNECTED, getRecipient(), sas.getSasText());
  }

  public void notifyConnectingtoInitiator() {
    sendMessage(Type.CONNECTING_TO_INITIATOR, getRecipient(), null);
  }

  public void notifyCallDisconnected() {
    if (state == STATE_RINGING)
      handleMissedCall(remoteNumber);

    sendMessage(Type.CALL_DISCONNECTED, getRecipient(), null);
    this.terminate();
  }

  public void notifyHandshakeFailed() {
    state = STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(Type.HANDSHAKE_FAILED, getRecipient(), null);
    this.terminate();
  }

  public void notifyRecipientUnavailable() {
    state = STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(Type.RECIPIENT_UNAVAILABLE, getRecipient(), null);
    this.terminate();
  }

  public void notifyPerformingHandshake() {
    outgoingRinger.playHandshake();
    sendMessage(Type.PERFORMING_HANDSHAKE, getRecipient(), null);
  }

  public void notifyServerFailure() {
    if (state == STATE_RINGING)
      handleMissedCall(remoteNumber);

    state = STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(Type.SERVER_FAILURE, getRecipient(), null);
    this.terminate();
  }

  public void notifyClientFailure() {
    if (state == STATE_RINGING)
      handleMissedCall(remoteNumber);

    state = STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(Type.CLIENT_FAILURE, getRecipient(), null);
    this.terminate();
  }

  public void notifyLoginFailed() {
    if (state == STATE_RINGING)
      handleMissedCall(remoteNumber);

    state = STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(Type.LOGIN_FAILED, getRecipient(), null);
    this.terminate();
  }

  public void notifyNoSuchUser() {
    sendMessage(Type.NO_SUCH_USER, getRecipient(), null);
    this.terminate();
  }

  public void notifyServerMessage(String message) {
    sendMessage(Type.SERVER_MESSAGE, getRecipient(), message);
    this.terminate();
  }

  public void notifyClientError(String msg) {
    sendMessage(Type.CLIENT_FAILURE, getRecipient(), msg);
    this.terminate();
  }

  public void notifyCallConnecting() {
    outgoingRinger.playSonar();
  }

  public void notifyWaitingForResponder() {}

  private void sendMessage(@NonNull Type type,
                           @NonNull Recipient recipient,
                           @Nullable String error)
  {
    EventBus.getDefault().postSticky(new RedPhoneEvent(type, recipient, error));
  }

  private class IntentRunnable implements Runnable {
    private final Intent intent;

    public IntentRunnable(Intent intent) {
      this.intent = intent;
    }

    public void run() {
      onIntentReceived(intent);
    }
  }

  @Override
  public boolean isInCall() {
    switch(state) {
      case STATE_IDLE:
        return false;
      case STATE_DIALING:
      case STATE_RINGING:
      case STATE_ANSWERING:
      case STATE_CONNECTED:
        return true;
      default:
        Log.e(TAG, "Unhandled call state: " + state);
        return false;
    }
  }

  private static class ProximityLockRelease implements Thread.UncaughtExceptionHandler {
    private final LockManager lockManager;

    private ProximityLockRelease(LockManager lockManager) {
      this.lockManager = lockManager;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
      Log.d(TAG, "Uncaught exception - releasing proximity lock", throwable);
      lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
    }
  }
}
