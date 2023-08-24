package org.thoughtcrime.securesms.audio;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;

public class AudioRecorder {

  private static final String TAG = Log.tag(AudioRecorder.class);

  private static final ExecutorService executor = SignalExecutors.newCachedSingleThreadExecutor("signal-AudioRecorder", ThreadUtil.PRIORITY_UI_BLOCKING_THREAD);

  private final Context                   context;
  private final AudioRecordingHandler     uiHandler;
  private final AudioRecorderFocusManager audioFocusManager;

  private Recorder    recorder;
  private Future<Uri> recordingUriFuture;

  private SingleSubject<VoiceNoteDraft> recordingSubject;

  public AudioRecorder(@NonNull Context context, @Nullable AudioRecordingHandler uiHandler) {
    this.context   = context;
    this.uiHandler = uiHandler;

    AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener;

    if (this.uiHandler != null) {
      onAudioFocusChangeListener = focusChange -> {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
          Log.i(TAG, "Audio focus change " + focusChange + " stopping recording via UI handler.");
          this.uiHandler.onRecordCanceled(false);
        }
      };
    } else {
      onAudioFocusChangeListener = focusChange -> {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
          Log.i(TAG, "Audio focus change " + focusChange + " stopping recording.");
          stopRecording();
        }
      };
    }
    audioFocusManager = AudioRecorderFocusManager.create(context, onAudioFocusChangeListener);
  }

  public @NonNull Single<VoiceNoteDraft> startRecording() {
    return startRecording(Build.VERSION.SDK_INT >= 26);
  }

  public @NonNull Single<VoiceNoteDraft> startRecording(final boolean useMediaCodecWrapper) {
    Log.i(TAG, "startRecording(" + useMediaCodecWrapper + ")");

    final SingleSubject<VoiceNoteDraft> recordingSingle = SingleSubject.create();
    startRecordingInternal(useMediaCodecWrapper, recordingSingle);

    return recordingSingle;
  }

  private void startRecordingInternal(boolean useMediaRecorderWrapper, SingleSubject<VoiceNoteDraft> recordingSingle) {
    executor.execute(() -> {
      Log.i(TAG, "Running startRecording(" + useMediaRecorderWrapper + ") + " + Thread.currentThread().getId());
      try {
        if (recorder != null) {
          recordingSingle.onError(new IllegalStateException("We can only do one recording at a time!"));
          return;
        }

        ParcelFileDescriptor fds[] = ParcelFileDescriptor.createPipe();

        recordingUriFuture = BlobProvider.getInstance()
                                       .forData(new ParcelFileDescriptor.AutoCloseInputStream(fds[0]), 0)
                                       .withMimeType(MediaUtil.AUDIO_AAC)
                                       .createForDraftAttachmentAsync(context);

        recorder = useMediaRecorderWrapper ? new MediaRecorderWrapper() : new AudioCodec();
        int focusResult = audioFocusManager.requestAudioFocus();
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
          Log.w(TAG, "Could not gain audio focus. Received result code " + focusResult);
        }
        recorder.start(fds[1]);
        this.recordingSubject = recordingSingle;
      } catch (IOException | RuntimeException e) {
        Log.w(TAG, e);
        recordingUriFuture = null;
        recorder = null;
        audioFocusManager.abandonAudioFocus();
        if (useMediaRecorderWrapper) {
          startRecordingInternal(false, recordingSingle);
        } else {
          recordingSingle.onError(e);
        }
      }
    });
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
        Uri uri = recordingUriFuture.get();
        long size = MediaUtil.getMediaSize(context, uri);
        recordingSubject.onSuccess(new VoiceNoteDraft(uri, size));
      } catch (IOException | ExecutionException | InterruptedException ioe) {
        Log.w(TAG, ioe);
        recordingSubject.onError(ioe);
      }

      recordingSubject   = null;
      recorder           = null;
      recordingUriFuture = null;
    });
  }
}
