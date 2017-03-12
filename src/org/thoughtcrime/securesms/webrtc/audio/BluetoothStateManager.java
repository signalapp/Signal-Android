package org.thoughtcrime.securesms.webrtc.audio;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.thoughtcrime.securesms.util.ServiceUtil;

import java.util.List;

public class BluetoothStateManager {

  private static final String TAG = BluetoothStateManager.class.getSimpleName();

  private enum ScoConnection {
    DISCONNECTED,
    IN_PROGRESS,
    CONNECTED
  }

  private final Object LOCK = new Object();

  private final Context                     context;
  private final BluetoothAdapter            bluetoothAdapter;
  private final BluetoothScoReceiver        bluetoothScoReceiver;
  private final BluetoothConnectionReceiver bluetoothConnectionReceiver;
  private final BluetoothStateListener      listener;

  private BluetoothHeadset bluetoothHeadset = null;
  private ScoConnection    scoConnection    = ScoConnection.DISCONNECTED;
  private boolean          wantsConnection  = false;

  public BluetoothStateManager(@NonNull Context context, @Nullable BluetoothStateListener listener) {
    this.context                     = context.getApplicationContext();
    this.bluetoothAdapter            = BluetoothAdapter.getDefaultAdapter();
    this.bluetoothScoReceiver        = new BluetoothScoReceiver();
    this.bluetoothConnectionReceiver = new BluetoothConnectionReceiver();
    this.listener                    = listener;

    if (this.bluetoothAdapter == null)
      return;

    requestHeadsetProxyProfile();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      context.registerReceiver(bluetoothConnectionReceiver, new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED));
    }

    Intent sticky = context.registerReceiver(bluetoothScoReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));

    if (sticky != null) {
      bluetoothScoReceiver.onReceive(context, sticky);
    }

    handleBluetoothStateChange();
  }

  public void onDestroy() {
    if (bluetoothHeadset != null && bluetoothAdapter != null && Build.VERSION.SDK_INT >= 11) {
      this.bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
    }

    if (Build.VERSION.SDK_INT >= 11 && bluetoothConnectionReceiver != null) {
      context.unregisterReceiver(bluetoothConnectionReceiver);
    }

    if (bluetoothScoReceiver != null) {
      context.unregisterReceiver(bluetoothScoReceiver);
    }

    this.bluetoothHeadset = null;
  }

  public void setWantsConnection(boolean enabled) {
    synchronized (LOCK) {
      AudioManager audioManager = ServiceUtil.getAudioManager(context);

      this.wantsConnection = enabled;

      if (wantsConnection && isBluetoothAvailable() && scoConnection == ScoConnection.DISCONNECTED) {
        audioManager.startBluetoothSco();
        scoConnection = ScoConnection.IN_PROGRESS;
      } else if (!wantsConnection && scoConnection == ScoConnection.CONNECTED) {
        audioManager.stopBluetoothSco();
        audioManager.setBluetoothScoOn(false);
        scoConnection = ScoConnection.DISCONNECTED;
      } else if (!wantsConnection && scoConnection == ScoConnection.IN_PROGRESS) {
        audioManager.stopBluetoothSco();
        scoConnection = ScoConnection.DISCONNECTED;
      }
    }
  }

  private void handleBluetoothStateChange() {
    if (listener != null) listener.onBluetoothStateChanged(isBluetoothAvailable());
  }

  private boolean isBluetoothAvailable() {
    try {
      synchronized (LOCK) {
        AudioManager audioManager = ServiceUtil.getAudioManager(context);

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) return false;
        if (!audioManager.isBluetoothScoAvailableOffCall())            return false;

        if (Build.VERSION.SDK_INT >= 11) {
          return bluetoothHeadset != null && !bluetoothHeadset.getConnectedDevices().isEmpty();
        } else {
          return audioManager.isBluetoothScoOn() || audioManager.isBluetoothA2dpOn();
        }
      }
    } catch (Exception e) {
      Log.w(TAG, e);
      return false;
    }
  }

  private String getScoChangeIntent() {
    if (Build.VERSION.SDK_INT >= 14) {
      return AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED;
    } else {
      return AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED;
    }
  }


  private void requestHeadsetProxyProfile() {
    if (Build.VERSION.SDK_INT >= 11) {
      this.bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
        @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
          if (profile == BluetoothProfile.HEADSET) {
            synchronized (LOCK) {
              bluetoothHeadset = (BluetoothHeadset) proxy;
            }

            Intent sticky = context.registerReceiver(null, new IntentFilter(getScoChangeIntent()));
            bluetoothScoReceiver.onReceive(context, sticky);

            synchronized (LOCK) {
              if (wantsConnection && isBluetoothAvailable() && scoConnection == ScoConnection.DISCONNECTED) {
                AudioManager audioManager = ServiceUtil.getAudioManager(context);
                audioManager.startBluetoothSco();
                scoConnection = ScoConnection.IN_PROGRESS;
              }
            }

            handleBluetoothStateChange();
          }
        }

        @Override
        public void onServiceDisconnected(int profile) {
          Log.w(TAG, "onServiceDisconnected");
          if (profile == BluetoothProfile.HEADSET) {
            bluetoothHeadset = null;
            handleBluetoothStateChange();
          }
        }
      }, BluetoothProfile.HEADSET);
    }
  }

  private class BluetoothScoReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent == null) return;
      Log.w(TAG, "onReceive");

      synchronized (LOCK) {
        if (getScoChangeIntent().equals(intent.getAction())) {
          int status = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR);

          if (status == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
            if (Build.VERSION.SDK_INT >= 11 && bluetoothHeadset != null) {
              List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();

              for (BluetoothDevice device : devices) {
                if (bluetoothHeadset.isAudioConnected(device)) {
                  int deviceClass = device.getBluetoothClass().getDeviceClass();

                  if (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE ||
                      deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                      deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET)
                  {
                    scoConnection = ScoConnection.CONNECTED;

                    if (wantsConnection) {
                      AudioManager audioManager = ServiceUtil.getAudioManager(context);
                      audioManager.setBluetoothScoOn(true);
                    }
                  }
                }
              }

            }
          }
        }
      }

      handleBluetoothStateChange();
    }
  }

  private class BluetoothConnectionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w(TAG, "onReceive");
      handleBluetoothStateChange();
    }
  }

  public interface BluetoothStateListener {
    public void onBluetoothStateChanged(boolean isAvailable);
  }

}
