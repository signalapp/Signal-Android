package org.thoughtcrime.securesms.webrtc.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.ServiceUtil;

public abstract class AudioManagerCompat {

  private static final String TAG = Log.tag(AudioManagerCompat.class);

  private static final int AUDIOFOCUS_GAIN = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;

  protected final AudioManager audioManager;

  @SuppressWarnings("CodeBlock2Expr")
  protected final AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = focusChange -> {
    Log.i(TAG, "onAudioFocusChangeListener: " + focusChange);
  };

  private AudioManagerCompat(@NonNull Context context) {
    audioManager = ServiceUtil.getAudioManager(context);
  }

  abstract public SoundPool createSoundPool();
  abstract public void requestCallAudioFocus();
  abstract public void abandonCallAudioFocus();

  public static AudioManagerCompat create(@NonNull Context context) {
    if (Build.VERSION.SDK_INT >= 26) {
      return new Api26AudioManagerCompat(context);
    } else if (Build.VERSION.SDK_INT >= 21) {
      return new Api21AudioManagerCompat(context);
    } else {
      return new Api19AudioManagerCompat(context);
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
    public void requestCallAudioFocus() {
      if (audioFocusRequest != null) {
        Log.w(TAG, "Already requested audio focus. Ignoring...");
        return;
      }

      audioFocusRequest = new AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
                                               .setAudioAttributes(AUDIO_ATTRIBUTES)
                                               .setOnAudioFocusChangeListener(onAudioFocusChangeListener)
                                               .build();

      int result = audioManager.requestAudioFocus(audioFocusRequest);

      if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        Log.w(TAG, "Audio focus not granted. Result code: " + result);
      }
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

      audioFocusRequest = null;
    }
  }

  @RequiresApi(21)
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
      return new SoundPool(1, AudioManager.STREAM_VOICE_CALL, 0);
    }

    @Override
    public void requestCallAudioFocus() {
      int result = audioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AUDIOFOCUS_GAIN);

      if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        Log.w(TAG, "Audio focus not granted. Result code: " + result);
      }
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
