package org.thoughtcrime.securesms.webrtc.audio;


import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;

public class SignalAudioManager {

  @SuppressWarnings("unused")
  private static final String TAG = SignalAudioManager.class.getSimpleName();

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
    AudioManager audioManager = ContextCompat.getSystemService(context, AudioManager.class);
    boolean      speaker      = !audioManager.isWiredHeadsetOn() && !audioManager.isBluetoothScoOn();

    audioManager.setMode(AudioManager.MODE_RINGTONE);
    audioManager.setMicrophoneMute(false);
    audioManager.setSpeakerphoneOn(speaker);

    incomingRinger.start(ringtoneUri, vibrate);
  }

  public void startOutgoingRinger(OutgoingRinger.Type type) {
    AudioManager audioManager = ContextCompat.getSystemService(context, AudioManager.class);
    audioManager.setMicrophoneMute(false);

    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

    outgoingRinger.start(type);
  }

  public void silenceIncomingRinger() {
    incomingRinger.stop();
  }

  public void startCommunication(boolean preserveSpeakerphone) {
    AudioManager audioManager = ContextCompat.getSystemService(context, AudioManager.class);

    incomingRinger.stop();
    outgoingRinger.stop();

    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

    if (!preserveSpeakerphone) {
      audioManager.setSpeakerphoneOn(false);
    }

    soundPool.play(connectedSoundId, 1.0f, 1.0f, 0, 0, 1.0f);
  }

  public void stop(boolean playDisconnected) {
    AudioManager audioManager = ContextCompat.getSystemService(context, AudioManager.class);

    incomingRinger.stop();
    outgoingRinger.stop();

    if (playDisconnected) {
      soundPool.play(disconnectedSoundId, 1.0f, 1.0f, 0, 0, 1.0f);
    }

    audioManager.setMode(AudioManager.MODE_NORMAL);

    audioManagerCompat.abandonCallAudioFocus();
  }
}
