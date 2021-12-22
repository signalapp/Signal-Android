package org.thoughtcrime.securesms.audio;

import android.media.MediaRecorder;
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
    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
    recorder.setOutputFile(fileDescriptor.getFileDescriptor());
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
    recorder.setAudioSamplingRate(SAMPLE_RATE);
    recorder.setAudioEncodingBitRate(BIT_RATE);
    recorder.setAudioChannels(CHANNELS);
    recorder.prepare();
    recorder.start();
  }

  @Override
  public void stop() {
    recorder.stop();
    recorder.release();
    recorder = null;
  }
}
