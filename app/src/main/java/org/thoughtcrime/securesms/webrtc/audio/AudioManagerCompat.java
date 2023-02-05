package org.thoughtcrime.securesms.webrtc.audio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.jetbrains.annotations.Nullable;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.util.List;

public abstract class AudioManagerCompat {

  private static final String TAG = Log.tag(AudioManagerCompat.class);

  private static final int AUDIOFOCUS_GAIN = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;

  protected final AudioManager audioManager;
  protected       boolean      hasFocus;

  @SuppressWarnings("CodeBlock2Expr")
  protected final AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = focusChange -> {
    Log.i(TAG, "onAudioFocusChangeListener: " + focusChange);
    hasFocus = focusChange == AudioManager.AUDIOFOCUS_GAIN;
  };

  private AudioManagerCompat(@NonNull Context context) {
    audioManager = ServiceUtil.getAudioManager(context);
  }

  public boolean isBluetoothScoAvailableOffCall() {
    return audioManager.isBluetoothScoAvailableOffCall();
  }

  public void startBluetoothSco() {
    audioManager.startBluetoothSco();
  }

  public void stopBluetoothSco() {
    audioManager.stopBluetoothSco();
  }

  public boolean isBluetoothConnected() {
    if (Build.VERSION.SDK_INT >= 31) {
      final SignalAudioManager.AudioDevice audioDevice = AudioDeviceMapping.fromPlatformType(audioManager.getCommunicationDevice().getType());
      return SignalAudioManager.AudioDevice.BLUETOOTH == audioDevice;
    } else {
      return isBluetoothScoOn();
    }
  }

  public boolean isBluetoothScoOn() {
    return audioManager.isBluetoothScoOn();
  }

  public void setBluetoothScoOn(boolean on) {
    audioManager.setBluetoothScoOn(on);
  }

  public int getMode() {
    return audioManager.getMode();
  }

  public void setMode(int modeInCommunication) {
    audioManager.setMode(modeInCommunication);
  }

  public boolean isSpeakerphoneOn() {
    return audioManager.isSpeakerphoneOn();
  }

  public void setSpeakerphoneOn(boolean on) {
    audioManager.setSpeakerphoneOn(on);
  }

  public boolean isMicrophoneMute() {
    return audioManager.isMicrophoneMute();
  }

  public void setMicrophoneMute(boolean on) {
    audioManager.setMicrophoneMute(on);
  }

  public boolean hasEarpiece(@NonNull Context context) {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
  }

  @RequiresApi(31)
  public List<AudioDeviceInfo> getAvailableCommunicationDevices() {
    return audioManager.getAvailableCommunicationDevices();
  }

  @RequiresApi(31)
  @Nullable
  public AudioDeviceInfo getCommunicationDevice() {
    return audioManager.getCommunicationDevice();
  }

  @RequiresApi(31)
  public boolean setCommunicationDevice(@Nullable AudioDeviceInfo device) {
    return audioManager.setCommunicationDevice(device);
  }

  @RequiresApi(31)
  public void clearCommunicationDevice() {
    audioManager.clearCommunicationDevice();
  }

  @RequiresApi(23)
  public void registerAudioDeviceCallback(@NonNull AudioDeviceCallback deviceCallback, @NonNull Handler handler) {
    audioManager.registerAudioDeviceCallback(deviceCallback, handler);
  }

  @RequiresApi(23)
  public void unregisterAudioDeviceCallback(@NonNull AudioDeviceCallback deviceCallback) {
    audioManager.unregisterAudioDeviceCallback(deviceCallback);
  }

  @SuppressLint("WrongConstant")
  public boolean isWiredHeadsetOn() {
    if (Build.VERSION.SDK_INT < 23) {
      //noinspection deprecation
      return audioManager.isWiredHeadsetOn();
    } else {
      AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
      for (AudioDeviceInfo device : devices) {
        final int type = device.getType();
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
          return true;
        } else if (type == AudioDeviceInfo.TYPE_USB_DEVICE) {
          return true;
        }
      }
      return false;
    }
  }

  public float ringVolumeWithMinimum() {
    int   currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
    int   maxVolume     = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
    float volume        = logVolume(currentVolume, maxVolume);
    float minVolume     = logVolume(15, 100);
    return Math.max(volume, minVolume);
  }

  private static float logVolume(int volume, int maxVolume) {
    if (maxVolume == 0 || volume > maxVolume) {
      return 0.5f;
    }
    return (float) (1 - (Math.log(maxVolume + 1 - volume) / Math.log(maxVolume + 1)));
  }

  abstract public SoundPool createSoundPool();
  abstract public boolean requestCallAudioFocus();
  abstract public void abandonCallAudioFocus();

  public static AudioManagerCompat create(@NonNull Context context) {
    if (Build.VERSION.SDK_INT >= 26) {
      return new Api26AudioManagerCompat(context);
    } else {
      return new Api21AudioManagerCompat(context);
    }
  }

  @RequiresApi(26)
  private static class Api26AudioManagerCompat extends AudioManagerCompat {

    private static AudioAttributes AUDIO_ATTRIBUTES = new AudioAttributes.Builder()
                                                                         .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                                         .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                                                         .build();

    private AudioFocusRequest audioFocusRequest;

    private Api26AudioManagerCompat(@NonNull Context context) {
      super(context);
    }

    @Override
    public SoundPool createSoundPool() {
      return new SoundPool.Builder()
                          .setAudioAttributes(AUDIO_ATTRIBUTES)
                          .setMaxStreams(1)
                          .build();
    }

    @Override
    public boolean requestCallAudioFocus() {
      if (audioFocusRequest != null && hasFocus) {
        Log.w(TAG, "Already requested audio focus. Ignoring...");
        return true;
      }

      if (audioFocusRequest == null) {
        audioFocusRequest = new AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
                                                 .setAudioAttributes(AUDIO_ATTRIBUTES)
                                                 .setOnAudioFocusChangeListener(onAudioFocusChangeListener)
                                                 .build();
      } else {
        Log.w(TAG, "Trying again to request audio focus");
      }

      int result = audioManager.requestAudioFocus(audioFocusRequest);

      if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        Log.w(TAG, "Audio focus not granted. Result code: " + result);
        return false;
      }

      return true;
    }

    @Override
    public void abandonCallAudioFocus() {
      if (audioFocusRequest == null) {
        Log.w(TAG, "Don't currently have audio focus. Ignoring...");
        return;
      }

      int result = audioManager.abandonAudioFocusRequest(audioFocusRequest);

      if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        Log.w(TAG, "Audio focus abandon failed. Result code: " + result);
      }

      hasFocus          = false;
      audioFocusRequest = null;
    }
  }

  private static class Api21AudioManagerCompat extends Api19AudioManagerCompat {

    private static AudioAttributes AUDIO_ATTRIBUTES = new AudioAttributes.Builder()
                                                                         .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                                         .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                                                         .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                                                                         .build();

    private Api21AudioManagerCompat(@NonNull Context context) {
      super(context);
    }

    @Override
    public SoundPool createSoundPool() {
      return new SoundPool.Builder()
                          .setAudioAttributes(AUDIO_ATTRIBUTES)
                          .setMaxStreams(1)
                          .build();
    }
  }

  private static class Api19AudioManagerCompat extends AudioManagerCompat {

    private Api19AudioManagerCompat(@NonNull Context context) {
      super(context);
    }

    @Override
    public SoundPool createSoundPool() {
      return new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
    }

    @Override
    public boolean requestCallAudioFocus() {
      int result = audioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AUDIOFOCUS_GAIN);

      if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        Log.w(TAG, "Audio focus not granted. Result code: " + result);
        return false;
      }

      return true;
    }

    @Override
    public void abandonCallAudioFocus() {
      int result = audioManager.abandonAudioFocus(onAudioFocusChangeListener);

      if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        Log.w(TAG, "Audio focus abandon failed. Result code: " + result);
      }
    }
  }
}
