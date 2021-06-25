package org.thoughtcrime.securesms.webrtc.audio;


import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ServiceUtil;

public class SignalAudioManager {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(SignalAudioManager.class);

  private final Context        context;
  private final IncomingRinger incomingRinger;
  private final OutgoingRinger outgoingRinger;

  private final SoundPool soundPool;
  private final int       connectedSoundId;
  private final int       disconnectedSoundId;

  private final AudioManagerCompat audioManagerCompat;

  public SignalAudioManager(@NonNull Context context) {
    this.context            = context.getApplicationContext();
    this.incomingRinger     = new IncomingRinger(context);
    this.outgoingRinger     = new OutgoingRinger(context);
    this.audioManagerCompat = AudioManagerCompat.create(context);
    this.soundPool          = audioManagerCompat.createSoundPool();

    this.connectedSoundId    = this.soundPool.load(context, R.raw.webrtc_completed, 1);
    this.disconnectedSoundId = this.soundPool.load(context, R.raw.webrtc_disconnected, 1);
  }

  public void initializeAudioForCall() {
    audioManagerCompat.requestCallAudioFocus();
  }

  public void startIncomingRinger(@Nullable Uri ringtoneUri, boolean vibrate) {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);
    boolean      speaker      = !audioManager.isWiredHeadsetOn() && !audioManager.isBluetoothScoOn();

    audioManager.setMode(AudioManager.MODE_RINGTONE);
    audioManager.setMicrophoneMute(false);
    audioManager.setSpeakerphoneOn(speaker);

    incomingRinger.start(ringtoneUri, vibrate);
  }

  public void startOutgoingRinger(OutgoingRinger.Type type) {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);
    audioManager.setMicrophoneMute(false);

    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

    outgoingRinger.start(type);
  }

  public void silenceIncomingRinger() {
    incomingRinger.stop();
  }

  public void startCommunication(boolean preserveSpeakerphone) {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);

    incomingRinger.stop();
    outgoingRinger.stop();

    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

    if (!preserveSpeakerphone) {
      audioManager.setSpeakerphoneOn(false);
    }

    float volume = ringVolumeWithMinimum(audioManager);
    soundPool.play(connectedSoundId, volume, volume, 0, 0, 1.0f);
  }

  public void stop(boolean playDisconnected) {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);

    incomingRinger.stop();
    outgoingRinger.stop();

    if (playDisconnected) {
      float volume = ringVolumeWithMinimum(audioManager);
      soundPool.play(disconnectedSoundId, volume, volume, 0, 0, 1.0f);
    }

    audioManager.setMode(AudioManager.MODE_NORMAL);

    audioManagerCompat.abandonCallAudioFocus();
  }

  private static float ringVolumeWithMinimum(@NonNull AudioManager audioManager) {
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
}
