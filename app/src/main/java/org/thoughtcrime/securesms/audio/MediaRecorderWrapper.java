package org.thoughtcrime.securesms.audio;

import android.media.MediaRecorder;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import org.signal.core.util.logging.Log;

import java.io.IOException;

/**
 * Wrap Android's {@link MediaRecorder} for use with voice notes.
 */
public class MediaRecorderWrapper implements Recorder {

  private static final String TAG = Log.tag(MediaRecorderWrapper.class);

  private static final int    SAMPLE_RATE       = 44100;
  private static final int    CHANNELS          = 1;
  private static final int    BIT_RATE          = 32000;

  private MediaRecorder recorder = null;

  @Override
  public void start(ParcelFileDescriptor fileDescriptor) throws IOException {
    Log.i(TAG, "Recording voice note using MediaRecorderWrapper.");
    recorder = new MediaRecorder();

    try {
      recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
      recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
      recorder.setOutputFile(fileDescriptor.getFileDescriptor());
      recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
      recorder.setAudioSamplingRate(getSampleRate());
      recorder.setAudioEncodingBitRate(BIT_RATE);
      recorder.setAudioChannels(CHANNELS);
      recorder.prepare();
      recorder.start();
    } catch (IllegalStateException e) {
      Log.w(TAG, "Unable to start recording", e);
      recorder.release();
      recorder = null;
      throw new IOException(e);
    }
  }

  @Override
  public void stop() {
    if (recorder == null) {
      return;
    }

    try {
      recorder.stop();
    } catch (RuntimeException e) {
      if (e.getClass() != RuntimeException.class) {
        throw e;
      } else {
        Log.d(TAG, "Recording stopped with no data captured.");
      }
    } finally {
      recorder.release();
      recorder = null;
    }
  }

  private static int getSampleRate() {
    if ("Xiaomi".equals(Build.MANUFACTURER) && "Mi 9T".equals(Build.MODEL)) {
      // Recordings sound robotic with the standard sample rate.
      return 44000;
    }
    return SAMPLE_RATE;
  }
}
