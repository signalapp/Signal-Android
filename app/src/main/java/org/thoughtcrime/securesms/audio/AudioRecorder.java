package org.thoughtcrime.securesms.audio;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;

public class AudioRecorder {

  private static final String TAG = Log.tag(AudioRecorder.class);

  private static final ExecutorService executor = SignalExecutors.newCachedSingleThreadExecutor("signal-AudioRecorder");

  private final Context                   context;
  private final AudioRecorderFocusManager audioFocusManager;

  private Recorder recorder;
  private Uri      captureUri;

  private SingleSubject<VoiceNoteDraft> recordingSubject;

  public AudioRecorder(@NonNull Context context) {
    this.context = context;
    audioFocusManager = AudioRecorderFocusManager.create(context, focusChange -> {
      Log.i(TAG, "Audio focus change " + focusChange + " stopping recording");
      stopRecording();
    });
  }

  public @NonNull Single<VoiceNoteDraft> startRecording() {
    Log.i(TAG, "startRecording()");

    final SingleSubject<VoiceNoteDraft> recordingSingle = SingleSubject.create();
    executor.execute(() -> {
      Log.i(TAG, "Running startRecording() + " + Thread.currentThread().getId());
      try {
        if (recorder != null) {
          throw new AssertionError("We can only record once at a time.");
        }

        ParcelFileDescriptor fds[] = ParcelFileDescriptor.createPipe();

        captureUri = BlobProvider.getInstance()
                                 .forData(new ParcelFileDescriptor.AutoCloseInputStream(fds[0]), 0)
                                 .withMimeType(MediaUtil.AUDIO_AAC)
                                 .createForDraftAttachmentAsync(context, () -> Log.i(TAG, "Write successful."), e -> Log.w(TAG, "Error during recording", e));
        recorder = Build.VERSION.SDK_INT >= 26 ? new MediaRecorderWrapper() : new AudioCodec();
        int focusResult = audioFocusManager.requestAudioFocus();
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
          Log.w(TAG, "Could not gain audio focus. Received result code " + focusResult);
        }
        recorder.start(fds[1]);
        this.recordingSubject = recordingSingle;
      } catch (IOException e) {
        recordingSingle.onError(e);
        recorder = null;
        Log.w(TAG, e);
      }
    });

    return recordingSingle;
  }

  public void stopRecording() {
    Log.i(TAG, "stopRecording()");

    executor.execute(() -> {
      if (recorder == null) {
        Log.e(TAG, "MediaRecorder was never initialized successfully!");
        return;
      }

      audioFocusManager.abandonAudioFocus();
      recorder.stop();

      try {
        long size = MediaUtil.getMediaSize(context, captureUri);
        recordingSubject.onSuccess(new VoiceNoteDraft(captureUri, size));
      } catch (IOException ioe) {
        Log.w(TAG, ioe);
        recordingSubject.onError(ioe);
      }

      recordingSubject = null;
      recorder         = null;
      captureUri       = null;
    });
  }
}
