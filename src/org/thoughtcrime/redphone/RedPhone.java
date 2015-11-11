/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2015 Open Whisper Systems
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.redphone.ui.CallControls;
import org.thoughtcrime.redphone.ui.CallScreen;
import org.thoughtcrime.redphone.util.AudioUtils;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.events.RedPhoneEvent;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import de.greenrobot.event.EventBus;

/**
 * The main UI class for RedPhone.  Most of the heavy lifting is
 * done by RedPhoneService, so this activity is mostly responsible
 * for receiving events about the state of ongoing calls and displaying
 * the appropriate UI components.
 *
 * @author Moxie Marlinspike
 *
 */
public class RedPhone extends Activity {

  private static final String TAG = RedPhone.class.getSimpleName();

  private static final int STANDARD_DELAY_FINISH    = 1000;
  public  static final int BUSY_SIGNAL_DELAY_FINISH = 5500;

  public static final String ANSWER_ACTION   = RedPhone.class.getName() + ".ANSWER_ACTION";
  public static final String DENY_ACTION     = RedPhone.class.getName() + ".DENY_ACTION";
  public static final String END_CALL_ACTION = RedPhone.class.getName() + ".END_CALL_ACTION";

  private CallScreen        callScreen;
  private BroadcastReceiver bluetoothStateReceiver;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.redphone);

    setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

    initializeResources();
  }


  @Override
  public void onResume() {
    super.onResume();

    initializeScreenshotSecurity();
    EventBus.getDefault().registerSticky(this);
    registerBluetoothReceiver();
  }

  @Override
  public void onNewIntent(Intent intent){
    if (ANSWER_ACTION.equals(intent.getAction())) {
      handleAnswerCall();
    } else if (DENY_ACTION.equals(intent.getAction())) {
      handleDenyCall();
    } else if (END_CALL_ACTION.equals(intent.getAction())) {
      handleEndCall();
    }
  }

  @Override
  public void onPause() {
    super.onPause();

    EventBus.getDefault().unregister(this);
    unregisterReceiver(bluetoothStateReceiver);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfiguration) {
    super.onConfigurationChanged(newConfiguration);
  }

  private void initializeScreenshotSecurity() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH &&
        TextSecurePreferences.isScreenSecurityEnabled(this))
    {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    } else {
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
  }

  private void initializeResources() {
    callScreen = (CallScreen)findViewById(R.id.callScreen);
    callScreen.setHangupButtonListener(new HangupButtonListener());
    callScreen.setIncomingCallActionListener(new IncomingCallActionListener());
    callScreen.setMuteButtonListener(new MuteButtonListener());
    callScreen.setAudioButtonListener(new AudioButtonListener());
  }

  private void handleSetMute(boolean enabled) {
    Intent intent = new Intent(this, RedPhoneService.class);
    intent.setAction(RedPhoneService.ACTION_SET_MUTE);
    intent.putExtra(RedPhoneService.EXTRA_MUTE, enabled);
    startService(intent);
  }

  private void handleAnswerCall() {
    RedPhoneEvent event = EventBus.getDefault().getStickyEvent(RedPhoneEvent.class);

    if (event != null) {
      callScreen.setActiveCall(event.getRecipient(), getString(org.thoughtcrime.securesms.R.string.RedPhone_answering));

      Intent intent = new Intent(this, RedPhoneService.class);
      intent.setAction(RedPhoneService.ACTION_ANSWER_CALL);
      startService(intent);
    }
  }

  private void handleDenyCall() {
    RedPhoneEvent event = EventBus.getDefault().getStickyEvent(RedPhoneEvent.class);

    if (event != null) {
      Intent intent = new Intent(this, RedPhoneService.class);
      intent.setAction(RedPhoneService.ACTION_DENY_CALL);
      startService(intent);

      callScreen.setActiveCall(event.getRecipient(), getString(org.thoughtcrime.securesms.R.string.RedPhone_ending_call));
      delayedFinish();
    }
  }

  private void handleEndCall() {
    Log.w(TAG, "Hangup pressed, handling termination now...");
    Intent intent = new Intent(RedPhone.this, RedPhoneService.class);
    intent.setAction(RedPhoneService.ACTION_HANGUP_CALL);
    startService(intent);

    RedPhoneEvent event = EventBus.getDefault().getStickyEvent(RedPhoneEvent.class);

    if (event != null) {
      RedPhone.this.handleTerminate(event.getRecipient());
    }
  }

  private void handleIncomingCall(@NonNull RedPhoneEvent event) {
    callScreen.setIncomingCall(event.getRecipient());
  }

  private void handleOutgoingCall(@NonNull RedPhoneEvent event) {
    callScreen.setActiveCall(event.getRecipient(), getString(org.thoughtcrime.securesms.R.string.RedPhone_dialing));
  }

  private void handleTerminate(@NonNull Recipient recipient /*, int terminationType */) {
    Log.w(TAG, "handleTerminate called");
    Log.w(TAG, "Termination Stack:", new Exception());

    callScreen.setActiveCall(recipient, getString(R.string.RedPhone_ending_call));
    EventBus.getDefault().removeStickyEvent(RedPhoneEvent.class);

    delayedFinish();
  }

  private void handleCallRinging(@NonNull RedPhoneEvent event) {
    callScreen.setActiveCall(event.getRecipient(), getString(R.string.RedPhone_ringing));
  }

  private void handleCallBusy(@NonNull RedPhoneEvent event) {
    callScreen.setActiveCall(event.getRecipient(), getString(R.string.RedPhone_busy));

    delayedFinish(BUSY_SIGNAL_DELAY_FINISH);
  }

  private void handleCallConnected(@NonNull RedPhoneEvent event) {
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);
    callScreen.setActiveCall(event.getRecipient(),
                             getString(R.string.RedPhone_connected),
                             event.getExtra());
  }

  private void handleConnectingToInitiator(@NonNull RedPhoneEvent event) {
    callScreen.setActiveCall(event.getRecipient(), getString(R.string.RedPhone_connecting));
  }

  private void handleHandshakeFailed(@NonNull RedPhoneEvent event) {
    callScreen.setActiveCall(event.getRecipient(), getString(R.string.RedPhone_handshake_failed));
    delayedFinish();
  }

  private void handleRecipientUnavailable(@NonNull RedPhoneEvent event) {
    callScreen.setActiveCall(event.getRecipient(), getString(R.string.RedPhone_recipient_unavailable));
    delayedFinish();
  }

  private void handlePerformingHandshake(@NonNull RedPhoneEvent event) {
    callScreen.setActiveCall(event.getRecipient(), getString(R.string.RedPhone_performing_handshake));
  }

  private void handleServerFailure(@NonNull RedPhoneEvent event) {
    callScreen.setActiveCall(event.getRecipient(), getString(R.string.RedPhone_network_failed));
    delayedFinish();
  }

  private void handleClientFailure(final @NonNull RedPhoneEvent event) {
    callScreen.setActiveCall(event.getRecipient(), getString(R.string.RedPhone_client_failed));
    if( event.getExtra() != null && !isFinishing() ) {
      AlertDialog.Builder ad = new AlertDialog.Builder(this);
      ad.setTitle(R.string.RedPhone_fatal_error);
      ad.setMessage(event.getExtra());
      ad.setCancelable(false);
      ad.setPositiveButton(android.R.string.ok, new OnClickListener() {
        public void onClick(DialogInterface dialog, int arg) {
          RedPhone.this.handleTerminate(event.getRecipient());
        }
      });
      ad.show();
    }
  }

  private void handleLoginFailed(@NonNull RedPhoneEvent event) {
    callScreen.setActiveCall(event.getRecipient(), getString(R.string.RedPhone_login_failed));
    delayedFinish();
  }

  private void handleServerMessage(final @NonNull RedPhoneEvent event) {
    if( isFinishing() ) return; //we're already shutting down, this might crash
    AlertDialog.Builder ad = new AlertDialog.Builder(this);
    ad.setTitle(R.string.RedPhone_message_from_the_server);
    ad.setMessage(event.getExtra());
    ad.setCancelable(false);
    ad.setPositiveButton(android.R.string.ok, new OnClickListener() {
      public void onClick(DialogInterface dialog, int arg) {
        RedPhone.this.handleTerminate(event.getRecipient());
      }
    });
    ad.show();
  }

  private void handleNoSuchUser(final @NonNull RedPhoneEvent event) {
    if (isFinishing()) return; // XXX Stuart added this check above, not sure why, so I'm repeating in ignorance. - moxie
    AlertDialogWrapper.Builder dialog = new AlertDialogWrapper.Builder(this);
    dialog.setTitle(R.string.RedPhone_number_not_registered);
    dialog.setIconAttribute(R.attr.dialog_alert_icon);
    dialog.setMessage(R.string.RedPhone_the_number_you_dialed_does_not_support_secure_voice);
    dialog.setCancelable(true);
    dialog.setPositiveButton(R.string.RedPhone_got_it, new OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        RedPhone.this.handleTerminate(event.getRecipient());
      }
    });
    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        RedPhone.this.handleTerminate(event.getRecipient());
      }
    });
    dialog.show();
  }

  private void delayedFinish() {
    delayedFinish(STANDARD_DELAY_FINISH);
  }

  private void delayedFinish(int delayMillis) {
    callScreen.postDelayed(new Runnable() {
      public void run() {
        RedPhone.this.finish();
      }
    }, delayMillis);
  }

  @SuppressWarnings("unused")
  public void onEventMainThread(final RedPhoneEvent event) {
    Log.w(TAG, "Got message from service: " + event.getType());

    switch (event.getType()) {
      case CALL_CONNECTED:          handleCallConnected(event);            break;
      case SERVER_FAILURE:          handleServerFailure(event);            break;
      case PERFORMING_HANDSHAKE:    handlePerformingHandshake(event);      break;
      case HANDSHAKE_FAILED:        handleHandshakeFailed(event);          break;
      case CONNECTING_TO_INITIATOR: handleConnectingToInitiator(event);    break;
      case CALL_RINGING:            handleCallRinging(event);              break;
      case CALL_DISCONNECTED:       handleTerminate(event.getRecipient()); break;
      case SERVER_MESSAGE:          handleServerMessage(event);            break;
      case NO_SUCH_USER:            handleNoSuchUser(event);               break;
      case RECIPIENT_UNAVAILABLE:   handleRecipientUnavailable(event);     break;
      case INCOMING_CALL:           handleIncomingCall(event);             break;
      case OUTGOING_CALL:           handleOutgoingCall(event);             break;
      case CALL_BUSY:               handleCallBusy(event);                 break;
      case LOGIN_FAILED:            handleLoginFailed(event);              break;
      case CLIENT_FAILURE:			    handleClientFailure(event);            break;
    }
  }

  private class HangupButtonListener implements CallControls.HangupButtonListener {
    public void onClick() {
      handleEndCall();
    }
  }

  private class MuteButtonListener implements CallControls.MuteButtonListener {
    @Override
    public void onToggle(boolean isMuted) {
      RedPhone.this.handleSetMute(isMuted);
    }
  }

  private void registerBluetoothReceiver() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(AudioUtils.getScoUpdateAction());
    bluetoothStateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        callScreen.notifyBluetoothChange();
      }
    };

    registerReceiver(bluetoothStateReceiver, filter);
    callScreen.notifyBluetoothChange();
  }

  private class AudioButtonListener implements CallControls.AudioButtonListener {
    @Override
    public void onAudioChange(AudioUtils.AudioMode mode) {
      switch(mode) {
        case DEFAULT:
          AudioUtils.enableDefaultRouting(RedPhone.this);
          break;
        case SPEAKER:
          AudioUtils.enableSpeakerphoneRouting(RedPhone.this);
          break;
        case HEADSET:
          AudioUtils.enableBluetoothRouting(RedPhone.this);
          break;
        default:
          throw new IllegalStateException("Audio mode " + mode + " is not supported.");
      }
    }
  }

  private class IncomingCallActionListener implements CallControls.IncomingCallActionListener {
    @Override
    public void onAcceptClick() {
      RedPhone.this.handleAnswerCall();
    }

    @Override
    public void onDenyClick() {
      RedPhone.this.handleDenyCall();
    }
  }

}