package org.thoughtcrime.securesms.webrtc.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;

import java.io.IOException;

public class OutgoingRinger {

  private static final String TAG = Log.tag(OutgoingRinger.class);

  public enum Type {
    RINGING,
    BUSY
  }

  private final Context context;

  private MediaPlayer mediaPlayer;

  public OutgoingRinger(@NonNull Context context) {
    this.context        = context;
  }

  public void start(Type type) {
    int soundId;

    if      (type == Type.RINGING) soundId = R.raw.signal_call_outring;
    else if (type == Type.BUSY)    soundId = R.raw.signal_call_busy;
    else throw new IllegalArgumentException("Not a valid sound type");

    if( mediaPlayer != null ) {
      mediaPlayer.release();
    }

    mediaPlayer = new MediaPlayer();

    if (Build.VERSION.SDK_INT <= 21) {
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
    } else {
      mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                                        .build());
    }
    mediaPlayer.setLooping(true);

    String packageName = context.getPackageName();
    Uri    dataUri     = Uri.parse("android.resource://" + packageName + "/" + soundId);

    try {
      mediaPlayer.setDataSource(context, dataUri);
      mediaPlayer.prepare();
      mediaPlayer.start();
    } catch (IllegalArgumentException | SecurityException | IllegalStateException | IOException e) {
      Log.w(TAG, e);
    }
  }

  public void stop() {
    if (mediaPlayer == null) return;
    mediaPlayer.release();
    mediaPlayer = null;
  }
}
