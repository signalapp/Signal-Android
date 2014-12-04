package org.thoughtcrime.securesms.websocket;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import com.codebutler.android_websockets.WebSocketClient;
import com.codebutler.android_websockets.WebSocketClient.Listener;
import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.Release;
import org.thoughtcrime.securesms.jobs.PushReceiveJob;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.textsecure.internal.util.Util;
import org.whispersystems.websocket.messages.protobuf.SubProtocol;

import java.io.IOException;
import java.net.URI;

import static org.whispersystems.websocket.messages.protobuf.SubProtocol.WebSocketMessage.Type.REQUEST;

public class PushService extends Service implements Listener {
  public static final String TAG = "WebSocket.PushService";

  public static final String ACTION_PING = "WS_PING";
  public static final String ACTION_CONNECT = "WS_CONNECT";
  public static final String ACTION_DISCONNECT = "WS_DISCONNECT";
  private static final String ACTION_ACKNOWLEDGE = "WS_ACKNOWLEDGE";
  private static final int TIMEOUT = 1;
  private static final int MILLIS = 1000;
  private static final int ERROR_LIMIT = 11; // 2^10 * 1000ms * 1 = 1024s ~= 17min

  private WebSocketClient mClient;
  private final IBinder mBinder = new Binder();
  private boolean mShutDown = false;
  private  WakeLock wakelock, onMessageWakeLock;
  private int errors = 0;

  public static Intent startIntent(Context context) {
    Intent i = new Intent(context, PushService.class);
    i.setAction(ACTION_CONNECT);
    return i;
  }

  public static Intent pingIntent(Context context) {
    Intent i = new Intent(context, PushService.class);
    i.setAction(ACTION_PING);
    return i;
  }

  public static Intent closeIntent(Context context) {
    Intent i = new Intent(context, PushService.class);
    i.setAction(ACTION_DISCONNECT);
    return i;
  }

  public static Intent ackIntent(Context context, SubProtocol.WebSocketMessage message) {
    Intent i = new Intent(context, PushService.class);
    i.setAction(ACTION_ACKNOWLEDGE);
    //i.putExtra("ack", message.toJSON());
    return i;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  @Override
  public void onCreate() {
    super.onCreate();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (mClient != null && mClient.isConnected()) mClient.disconnect();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (wakelock != null && !wakelock.isHeld())
      wakelock.acquire();
    else if (wakelock == null){
      wakelock = ((PowerManager) getSystemService(POWER_SERVICE))
                 .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
      wakelock.acquire();
    }
    mShutDown = false;

    if (!TextSecurePreferences.isPushRegistered(getApplicationContext()) || TextSecurePreferences.isGcmRegistered(getApplicationContext())) {
      Log.w(TAG, "Not push registered");
      wakelock.release();
      stopSelf();
      return START_NOT_STICKY;
    }

    if (intent == null || ACTION_CONNECT == intent.getAction() || Util.isEmpty(intent.getAction())) {
      ConnectivityManager conn = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo networkInfo = conn.getActiveNetworkInfo();
      if (networkInfo != null) {
        NetworkInfo.DetailedState state = networkInfo.getDetailedState();
        if (networkInfo.getDetailedState() != NetworkInfo.DetailedState.CONNECTED) {
          Log.w(TAG, "Not connected, reset");
          wakelock.release();
          stopSelf();
          return START_NOT_STICKY;
        }
      } else {
        Log.w(TAG, "No ActiveNetwork, reset");
        wakelock.release();
        stopSelf();
        return START_NOT_STICKY;
      }
      AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
      PendingIntent operation = PendingIntent.getService(this, 0, PushService.pingIntent(this), PendingIntent.FLAG_NO_CREATE);
      if (operation == null) {
        operation = PendingIntent.getService(this, 0, PushService.pingIntent(this), PendingIntent.FLAG_UPDATE_CURRENT);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 15 * 1000, operation);
      }
    }

    if (mClient == null) {
      WakeLock clientlock = ((PowerManager) getSystemService(POWER_SERVICE))
                            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG+".Client");
      mClient = WebSocketClientFactory.create(TextSecurePreferences.getLocalNumber(this),
                                              TextSecurePreferences.getPushServerPassword(this),
                                              this, null, clientlock, this);
    }
    if (intent != null) {
      if (ACTION_DISCONNECT.equals(intent.getAction())) {
        mShutDown = true;
        if (mClient.isConnected()) mClient.disconnect();
      } else if (!mClient.isConnected()) {
        mClient.connect();
        Log.w(TAG, "Connect Client");
      }
      if (ACTION_PING.equals(intent.getAction())) {
        if (mClient.isConnected()){
          mClient.ping();
        }
        else {
          Log.w(TAG, "Ping failed, client not connected");
        }
      }
    }

    wakelock.release();
    return START_STICKY;
  }

  public class Binder extends android.os.Binder {
    PushService getService() {
      return PushService.this;
    }
  }

  public synchronized boolean isConnected() {
    return mClient != null && mClient.isConnected();
  }


  @Override
  public void onConnect() {
   errors = 0;
   Log.w(TAG, "Connected to websocket");
  }

  @Override
  public void onPong(String message) {
  }

  @Override
  public synchronized void onDisconnect(int code, String reason) {
    Log.w(TAG, String.format("Disconnected! Code: %d Reason: %s", code, reason));
    if (!mShutDown) {
      startService(startIntent(this));
    } else {
      stopSelf();
    }
  }

  @Override
  public synchronized void onError(Exception e) {
    if (errors < ERROR_LIMIT){
      errors++;
    }
    int backoff = (1 << (errors - 1)); //Use bit-shifting for exponential calculation

    Log.w(TAG, "Websocket error; Restart in "+(backoff*TIMEOUT)+" seconds");

    AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    PendingIntent operation = PendingIntent.getService(this, 0, PushService.pingIntent(this), PendingIntent.FLAG_NO_CREATE);
    if (operation != null) {
      operation.cancel();
      am.cancel(operation);
    }
    PendingIntent startUp = PendingIntent.getService(this, 0, PushService.startIntent(this), PendingIntent.FLAG_UPDATE_CURRENT);
    am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + (backoff * TIMEOUT * MILLIS) ,startUp);
  }

  @Override
  public synchronized void onMessage(byte[] data) {
    Log.w(TAG,"onMessage: "+data);
    if (onMessageWakeLock != null && !onMessageWakeLock.isHeld())
      onMessageWakeLock.acquire();
    else if (onMessageWakeLock == null) {
      onMessageWakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                          .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG+".onMessage");
      onMessageWakeLock.acquire();
    }
    try {
      if (!TextSecurePreferences.isPushRegistered(this)) {
        Log.w(TAG, "Not push registered!");
        return;
      }

      SubProtocol.WebSocketMessage websocketMessage = SubProtocol.WebSocketMessage.parseFrom(data);
      Log.d(TAG,"websocketMessage: "+websocketMessage.toString());
      switch (websocketMessage.getType()){
        case REQUEST:
          processWebSocketRequestMessage(websocketMessage.getRequest());
          break;
        case RESPONSE:
          processWebSocketResponseMessage(websocketMessage.getRequest());
          break;
        case UNKNOWN:
        default:
          Log.w(TAG,"Client received an unknown or response type message");
          break;
      }
    }catch (Exception e) {
      Log.w(TAG, e);
    } finally {
      if (onMessageWakeLock != null && onMessageWakeLock.isHeld()) {
        onMessageWakeLock.release();
      }
    }
  }

  private void processWebSocketResponseMessage(SubProtocol.WebSocketRequestMessage request) {
    Log.w(TAG, "Client should not receive WebSocket Respond messages");
  }

  private void processWebSocketRequestMessage(SubProtocol.WebSocketRequestMessage request) {
    Log.w(TAG, "Got message with verb: "+request.getVerb()+" and path: "+request.getPath());
    sendWebSocketMessageAcknowledgement(request);
    if (request.getPath().equalsIgnoreCase("/api/v1/message") && request.getVerb().equalsIgnoreCase("PUT")){
      ApplicationContext.getInstance(getApplicationContext())
              .getJobManager()
              .add(new PushReceiveJob(getApplicationContext(), request.getBody().toStringUtf8()));
    }else{
      Log.w(TAG, "Unsupported WebSocket Request");
    }

  }

  private void sendWebSocketMessageAcknowledgement(SubProtocol.WebSocketRequestMessage request) {
    SubProtocol.WebSocketResponseMessage response =
            SubProtocol.WebSocketResponseMessage.newBuilder().setId(request.getId()).setStatus(200).setMessage("OK").build();
    SubProtocol.WebSocketMessage message = SubProtocol.WebSocketMessage.newBuilder().setResponse(response).setType(SubProtocol.WebSocketMessage.Type.RESPONSE).build();
    if(isConnected()) {
      Log.d(TAG, "Send ACK for message");
      mClient.send(message.toByteArray());
    }else {
      Log.w(TAG, "Could not sent ACK for message. Client not connected");
    }
  }

  @Override
  public synchronized void onMessage(String data) {
    Log.w(TAG, "String message: "+data);
  }
}
