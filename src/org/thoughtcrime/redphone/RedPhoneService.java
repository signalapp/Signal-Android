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
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
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
import org.thoughtcrime.redphone.util.Base64;
import org.thoughtcrime.redphone.util.UncaughtExceptionHandlerManager;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;

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

  public static final String EXTRA_REMOTE_NUMBER      = "remote_number";
  public static final String EXTRA_SESSION_DESCRIPTOR = "session_descriptor";
  public static final String EXTRA_MUTE               = "mute_value";

  public static final String ACTION_INCOMING_CALL = "org.thoughtcrime.redphone.RedPhoneService.INCOMING_CALL";
  public static final String ACTION_OUTGOING_CALL = "org.thoughtcrime.redphone.RedPhoneService.OUTGOING_CALL";
  public static final String ACTION_ANSWER_CALL   = "org.thoughtcrime.redphone.RedPhoneService.ANSWER_CALL";
  public static final String ACTION_DENY_CALL     = "org.thoughtcrime.redphone.RedPhoneService.DENYU_CALL";
  public static final String ACTION_HANGUP_CALL   = "org.thoughtcrime.redphone.RedPhoneService.HANGUP";
  public static final String ACTION_SET_MUTE      = "org.thoughtcrime.redphone.RedPhoneService.SET_MUTE";
  public static final String ACTION_CONFIRM_SAS   = "org.thoughtcrime.redphone.RedPhoneService.CONFIRM_SAS";

  private static final String TAG = RedPhoneService.class.getName();

  private final List<Message> bufferedEvents = new LinkedList<>();
  private final IBinder binder               = new RedPhoneServiceBinder();
  private final Handler serviceHandler       = new Handler();

  private OutgoingRinger outgoingRinger;
  private IncomingRinger incomingRinger;

  private int                             state;
  private byte[]                          zid;
  private String                          remoteNumber;
  private CallManager                     currentCallManager;
  private LockManager                     lockManager;
  private UncaughtExceptionHandlerManager uncaughtExceptionHandlerManager;

  private Handler                  handler;
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
    return binder;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    unregisterReceiver(pstnCallListener);
    NotificationBarManager.setCallEnded(this);
    uncaughtExceptionHandlerManager.unregister();
  }

  private synchronized void onIntentReceived(Intent intent) {
    Log.w("RedPhoneService", "Received Intent: " + intent.getAction());

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
    this.state            = RedPhone.STATE_IDLE;
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
    state        = RedPhone.STATE_RINGING;

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

    sendMessage(RedPhone.HANDLE_OUTGOING_CALL, getRecipient());

    state = RedPhone.STATE_DIALING;
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
      Log.w("RedPhoneService", "Duplicate incoming call signal, ignoring...");
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
      Log.w("RedPhoneService", e);
    }
  }

  private void handleMissedCall(String remoteNumber) {
    DatabaseFactory.getSmsDatabase(this).insertMissedCall(remoteNumber);
    NotificationBarManager.notifyMissedCall(this, remoteNumber);
  }

  private void handleAnswerCall(Intent intent) {
    state = RedPhone.STATE_ANSWERING;
    incomingRinger.stop();
    DatabaseFactory.getSmsDatabase(this).insertReceivedCall(remoteNumber);
    if (currentCallManager != null) {
      ((ResponderCallManager)this.currentCallManager).answer(true);
    }
  }

  private void handleDenyCall(Intent intent) {
    state = RedPhone.STATE_IDLE;
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
    return ((currentCallManager != null && state != RedPhone.STATE_IDLE) ||
             telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE);
  }

  private boolean isIdle() {
    return state == RedPhone.STATE_IDLE;
  }

  private void shutdownAudio() {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    am.setMode(AudioManager.MODE_NORMAL);
  }

  public int getState() {
    return state;
  }

  public SASInfo getCurrentCallSAS() {
    if (currentCallManager != null)
      return currentCallManager.getSasInfo();
    else
      return null;
  }

  public Recipient getRecipient() {
    if (remoteNumber != null) {
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
    Log.w("RedPhoneService", "termination stack", new Exception());
    lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
    NotificationBarManager.setCallEnded(this);

    incomingRinger.stop();
    outgoingRinger.stop();

    if (currentCallManager != null) {
      currentCallManager.terminate();
      currentCallManager = null;
    }

    shutdownAudio();

    state = RedPhone.STATE_IDLE;
    lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
  }

  public void setCallStateHandler(Handler handler) {
    this.handler = handler;

    if (handler != null) {
      for (Message message : bufferedEvents) {
        handler.sendMessage(message);
      }

      bufferedEvents.clear();
    }
  }

  ///////// CallStateListener Implementation

  public void notifyCallStale() {
    Log.w("RedPhoneService", "Got a stale call, probably an old SMS...");
    handleMissedCall(remoteNumber);
    this.terminate();
  }

  public void notifyCallFresh() {
    Log.w("RedPhoneService", "Good call, time to ring and display call card...");
    sendMessage(RedPhone.HANDLE_INCOMING_CALL, getRecipient());

    lockManager.updatePhoneState(LockManager.PhoneState.INTERACTIVE);

    startCallCardActivity();
    incomingRinger.start();

    NotificationBarManager.setCallInProgress(this);
  }

  public void notifyBusy() {
    Log.w("RedPhoneService", "Got busy signal from responder!");
    sendMessage(RedPhone.HANDLE_CALL_BUSY, null);
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
    sendMessage(RedPhone.HANDLE_CALL_RINGING, null);
  }

  public void notifyCallConnected(SASInfo sas) {
    outgoingRinger.playComplete();
    lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
    state = RedPhone.STATE_CONNECTED;
    synchronized( this ) {
      sendMessage(RedPhone.HANDLE_CALL_CONNECTED, sas);
      try {
        wait();
      } catch (InterruptedException e) {
        throw new AssertionError( "Wait interrupted in RedPhoneService" );
      }
    }
  }
  public void notifyCallConnectionUIUpdateComplete() {
    synchronized( this ) {
      this.notify();
    }
  }
  public void notifyDebugInfo(String info) {
    sendMessage(RedPhone.HANDLE_DEBUG_INFO, info);
  }

  public void notifyConnectingtoInitiator() {
    sendMessage(RedPhone.HANDLE_CONNECTING_TO_INITIATOR, null);
  }

  public void notifyCallDisconnected() {
    if (state == RedPhone.STATE_RINGING)
      handleMissedCall(remoteNumber);

    sendMessage(RedPhone.HANDLE_CALL_DISCONNECTED, null);
    this.terminate();
  }

  public void notifyHandshakeFailed() {
    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_HANDSHAKE_FAILED, null);
    this.terminate();
  }

  public void notifyRecipientUnavailable() {
    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_RECIPIENT_UNAVAILABLE, null);
    this.terminate();
  }

  public void notifyPerformingHandshake() {
    outgoingRinger.playHandshake();
    sendMessage(RedPhone.HANDLE_PERFORMING_HANDSHAKE, null);
  }

  public void notifyServerFailure() {
    if (state == RedPhone.STATE_RINGING)
      handleMissedCall(remoteNumber);

    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_SERVER_FAILURE, null);
    this.terminate();
  }

  public void notifyClientFailure() {
    if (state == RedPhone.STATE_RINGING)
      handleMissedCall(remoteNumber);

    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_CLIENT_FAILURE, null);
    this.terminate();
  }

  public void notifyLoginFailed() {
    if (state == RedPhone.STATE_RINGING)
      handleMissedCall(remoteNumber);

    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_LOGIN_FAILED, null);
    this.terminate();
  }

  public void notifyNoSuchUser() {
    sendMessage(RedPhone.HANDLE_NO_SUCH_USER, getRecipient());
    this.terminate();
  }

  public void notifyServerMessage(String message) {
    sendMessage(RedPhone.HANDLE_SERVER_MESSAGE, message);
    this.terminate();
  }

  public void notifyClientError(String msg) {
    sendMessage(RedPhone.HANDLE_CLIENT_FAILURE,msg);
    this.terminate();
  }

  public void notifyClientError(int messageId) {
    notifyClientError(getString(messageId));
  }

  public void notifyCallConnecting() {
    outgoingRinger.playSonar();
  }

  public void notifyWaitingForResponder() {}

  private void sendMessage(int code, Object extra) {
    Message message = Message.obtain();
    message.what    = code;
    message.obj     = extra;

    if (handler != null) handler.sendMessage(message);
    else    			       bufferedEvents.add(message);
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

  public class RedPhoneServiceBinder extends Binder {
    public RedPhoneService getService() {
      return RedPhoneService.this;
    }
  }

  @Override
  public boolean isInCall() {
    switch(state) {
      case RedPhone.STATE_IDLE:
        return false;
      case RedPhone.STATE_DIALING:
      case RedPhone.STATE_RINGING:
      case RedPhone.STATE_ANSWERING:
      case RedPhone.STATE_CONNECTED:
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
