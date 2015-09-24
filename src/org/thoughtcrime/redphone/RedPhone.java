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
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.redphone.crypto.zrtp.SASInfo;
import org.thoughtcrime.redphone.ui.CallControls;
import org.thoughtcrime.redphone.ui.CallScreen;
import org.thoughtcrime.redphone.util.AudioUtils;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

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

  private static final int REMOTE_TERMINATE = 0;
  private static final int LOCAL_TERMINATE  = 1;

  public static final int STATE_IDLE      = 0;
  public static final int STATE_RINGING   = 2;
  public static final int STATE_DIALING   = 3;
  public static final int STATE_ANSWERING = 4;
  public static final int STATE_CONNECTED = 5;

  private static final int STANDARD_DELAY_FINISH    = 3000;
  public  static final int BUSY_SIGNAL_DELAY_FINISH = 5500;

  public static final int HANDLE_CALL_CONNECTED          = 0;
  public static final int HANDLE_WAITING_FOR_RESPONDER   = 1;
  public static final int HANDLE_SERVER_FAILURE          = 2;
  public static final int HANDLE_PERFORMING_HANDSHAKE    = 3;
  public static final int HANDLE_HANDSHAKE_FAILED        = 4;
  public static final int HANDLE_CONNECTING_TO_INITIATOR = 5;
  public static final int HANDLE_CALL_DISCONNECTED       = 6;
  public static final int HANDLE_CALL_RINGING            = 7;
  public static final int HANDLE_SERVER_MESSAGE          = 9;
  public static final int HANDLE_RECIPIENT_UNAVAILABLE   = 10;
  public static final int HANDLE_INCOMING_CALL           = 11;
  public static final int HANDLE_OUTGOING_CALL           = 12;
  public static final int HANDLE_CALL_BUSY               = 13;
  public static final int HANDLE_LOGIN_FAILED            = 14;
  public static final int HANDLE_CLIENT_FAILURE          = 15;
  public static final int HANDLE_DEBUG_INFO              = 16;
  public static final int HANDLE_NO_SUCH_USER            = 17;

  private final Handler callStateHandler = new CallStateHandler();

  private int               state;
  private RedPhoneService   redPhoneService;
  private CallScreen        callScreen;
  private BroadcastReceiver bluetoothStateReceiver;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.redphone);

    setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

    initializeResources();
  }


  @Override
  public void onResume() {
    super.onResume();

    initializeServiceBinding();
    registerBluetoothReceiver();
  }


  @Override
  public void onPause() {
    super.onPause();

    unbindService(serviceConnection);
    unregisterReceiver(bluetoothStateReceiver);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfiguration) {
    super.onConfigurationChanged(newConfiguration);
  }

  private void initializeServiceBinding() {
    Log.w(TAG, "Binding to RedPhoneService...");
    Intent bindIntent = new Intent(this, RedPhoneService.class);
    bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  private void initializeResources() {
    callScreen = (CallScreen)findViewById(R.id.callScreen);
    state      = STATE_IDLE;

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
    state = STATE_ANSWERING;
    callScreen.setActiveCall(redPhoneService.getRecipient(), "Answering");

    Intent intent = new Intent(this, RedPhoneService.class);
    intent.setAction(RedPhoneService.ACTION_ANSWER_CALL);
    startService(intent);
  }

  private void handleDenyCall() {
    state = STATE_IDLE;

    Intent intent = new Intent(this, RedPhoneService.class);
    intent.setAction(RedPhoneService.ACTION_DENY_CALL);
    startService(intent);

    callScreen.setActiveCall(redPhoneService.getRecipient(), "Ending call");
    delayedFinish();
  }

  private void handleIncomingCall(Recipient recipient) {
    state = STATE_RINGING;
    callScreen.setIncomingCall(redPhoneService.getRecipient());
  }

  private void handleOutgoingCall(Recipient recipient) {
    state = STATE_DIALING;
    callScreen.setActiveCall(recipient, "Dialing");
  }

  private void handleTerminate( int terminationType ) {
    Log.w(TAG, "handleTerminate called");
    Log.w(TAG, "Termination Stack:", new Exception());

    if( state == STATE_DIALING ) {
      if (terminationType == LOCAL_TERMINATE) {
        callScreen.setActiveCall(redPhoneService.getRecipient(), "Canceling call");
      } else {
        callScreen.setActiveCall(redPhoneService.getRecipient(), "Call rejected");
      }
    } else if (state != STATE_IDLE) {
      callScreen.setActiveCall(redPhoneService.getRecipient(), "Ending call");
    }

    state = STATE_IDLE;
    delayedFinish();
  }

  private void handleCallRinging() {
    callScreen.setActiveCall(redPhoneService.getRecipient(), "Ringing");
  }

  private void handleCallBusy() {
    callScreen.setActiveCall(redPhoneService.getRecipient(), "Busy");

    state = STATE_IDLE;
    delayedFinish(BUSY_SIGNAL_DELAY_FINISH);
  }

  private void handleCallConnected(SASInfo sas) {
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);
    callScreen.setActiveCall(redPhoneService.getRecipient(), "Connected", sas);

    state = STATE_CONNECTED;
    redPhoneService.notifyCallConnectionUIUpdateComplete();
  }

  private void handleDebugInfo( String info ) {
//    debugCard.setInfo( info );
  }

  private void handleConnectingToInitiator() {
    callScreen.setActiveCall(redPhoneService.getRecipient(), "Connecting");
  }

  private void handleHandshakeFailed() {
    state = STATE_IDLE;
    callScreen.setActiveCall(redPhoneService.getRecipient(), "Handshake failed!");
    delayedFinish();
  }

  private void handleRecipientUnavailable() {
    state = STATE_IDLE;
    callScreen.setActiveCall(redPhoneService.getRecipient(), "Recipient unavailable");
    delayedFinish();
  }

  private void handlePerformingHandshake() {
    callScreen.setActiveCall(redPhoneService.getRecipient(), "Performing handshake");
  }

  private void handleServerFailure() {
    state = STATE_IDLE;
    callScreen.setActiveCall(redPhoneService.getRecipient(), "Server failed!");
    delayedFinish();
  }

  private void handleClientFailure(String msg) {
    state = STATE_IDLE;
    callScreen.setActiveCall(redPhoneService.getRecipient(), "Client failed");
    if( msg != null && !isFinishing() ) {
      AlertDialog.Builder ad = new AlertDialog.Builder(this);
      ad.setTitle("Fatal Error");
      ad.setMessage(msg);
      ad.setCancelable(false);
      ad.setPositiveButton("Ok", new OnClickListener() {
        public void onClick(DialogInterface dialog, int arg) {
          RedPhone.this.handleTerminate(LOCAL_TERMINATE);
        }
      });
      ad.show();
    }
  }

  private void handleLoginFailed() {
    state = STATE_IDLE;
    callScreen.setActiveCall(redPhoneService.getRecipient(), "Login failed!");
    delayedFinish();
  }

  private void handleServerMessage(String message) {
    if( isFinishing() ) return; //we're already shutting down, this might crash
    AlertDialog.Builder ad = new AlertDialog.Builder(this);
    ad.setTitle("Message from the server");
    ad.setMessage(message);
    ad.setCancelable(false);
    ad.setPositiveButton(android.R.string.ok, new OnClickListener() {
      public void onClick(DialogInterface dialog, int arg) {
        RedPhone.this.handleTerminate(LOCAL_TERMINATE);
      }
    });
    ad.show();
  }

  private void handleNoSuchUser(final Recipient recipient) {
    if (isFinishing()) return; // XXX Stuart added this check above, not sure why, so I'm repeating in ignorance. - moxie
    AlertDialogWrapper.Builder dialog = new AlertDialogWrapper.Builder(this);
    dialog.setTitle("Number not registered!");
    dialog.setIconAttribute(R.attr.dialog_alert_icon);
    dialog.setMessage("The number you dialed does not support secure voice!");
    dialog.setCancelable(true);
    dialog.setPositiveButton("Got it", new OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        RedPhone.this.handleTerminate(LOCAL_TERMINATE);
      }
    });
    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        RedPhone.this.handleTerminate(LOCAL_TERMINATE);
      }
    });
    dialog.show();
  }

  private void delayedFinish() {
    delayedFinish(STANDARD_DELAY_FINISH);
  }

  private void delayedFinish(int delayMillis) {
    callStateHandler.postDelayed(new Runnable() {

    public void run() {
      RedPhone.this.finish();
    }}, delayMillis);
  }

  private class CallStateHandler extends Handler {
    @Override
    public void handleMessage(Message message) {
      Log.w(TAG, "Got message from service: " + message.what);
      switch (message.what) {
      case HANDLE_CALL_CONNECTED:          handleCallConnected((SASInfo)message.obj);               break;
      case HANDLE_SERVER_FAILURE:          handleServerFailure();                                   break;
      case HANDLE_PERFORMING_HANDSHAKE:    handlePerformingHandshake();                             break;
      case HANDLE_HANDSHAKE_FAILED:        handleHandshakeFailed();                                 break;
      case HANDLE_CONNECTING_TO_INITIATOR: handleConnectingToInitiator();                           break;
      case HANDLE_CALL_RINGING:            handleCallRinging();                                     break;
      case HANDLE_CALL_DISCONNECTED:       handleTerminate( REMOTE_TERMINATE );                     break;
      case HANDLE_SERVER_MESSAGE:          handleServerMessage((String)message.obj);                break;
      case HANDLE_NO_SUCH_USER:            handleNoSuchUser((Recipient)message.obj);                   break;
      case HANDLE_RECIPIENT_UNAVAILABLE:   handleRecipientUnavailable();                            break;
      case HANDLE_INCOMING_CALL:           handleIncomingCall((Recipient)message.obj);                 break;
      case HANDLE_OUTGOING_CALL:           handleOutgoingCall((Recipient)message.obj);                 break;
      case HANDLE_CALL_BUSY:               handleCallBusy();                                        break;
      case HANDLE_LOGIN_FAILED:            handleLoginFailed();                                     break;
      case HANDLE_CLIENT_FAILURE:			     handleClientFailure((String)message.obj);                break;
      case HANDLE_DEBUG_INFO:				       handleDebugInfo((String)message.obj);					          break;
      }
    }
  }

  private class HangupButtonListener implements CallControls.HangupButtonListener {
    public void onClick() {
      Log.w(TAG, "Hangup pressed, handling termination now...");
      Intent intent = new Intent(RedPhone.this, RedPhoneService.class);
      intent.setAction(RedPhoneService.ACTION_HANGUP_CALL);
      startService(intent);

      RedPhone.this.handleTerminate(LOCAL_TERMINATE);
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

  private ServiceConnection serviceConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service) {
      RedPhone.this.redPhoneService  = ((RedPhoneService.RedPhoneServiceBinder)service).getService();
      redPhoneService.setCallStateHandler(callStateHandler);

      Recipient recipient = redPhoneService.getRecipient();

      switch (redPhoneService.getState()) {
      case STATE_IDLE:      callScreen.reset();                                       break;
      case STATE_RINGING:   handleIncomingCall(recipient);                            break;
      case STATE_DIALING:   handleOutgoingCall(recipient);                            break;
      case STATE_ANSWERING: handleAnswerCall();                                       break;
      case STATE_CONNECTED: handleCallConnected(redPhoneService.getCurrentCallSAS()); break;
      }
    }

    public void onServiceDisconnected(ComponentName name) {
      redPhoneService.setCallStateHandler(null);
    }
  };
}