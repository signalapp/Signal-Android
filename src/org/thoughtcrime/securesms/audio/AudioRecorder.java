package org.thoughtcrime.securesms.audio;

import android.content.Context;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.JobParameters;

import java.io.IOException;

public class AudioRecorder {

  private static final String TAG = AudioRecorder.class.getSimpleName();

  private final Context                context;
  private final MasterSecret           masterSecret;
  private final PersistentBlobProvider blobProvider;

  private MediaRecorder        mediaRecorder;
  private Uri                  captureUri;
  private ParcelFileDescriptor fd;

  public AudioRecorder(@NonNull Context context, @NonNull MasterSecret masterSecret) {
    this.context      = context;
    this.masterSecret = masterSecret;
    this.blobProvider = PersistentBlobProvider.getInstance(context.getApplicationContext());
  }

  public void startRecording() throws IOException {
    Log.w(TAG, "startRecording()");

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new StartRecordingJob());
  }

  public @NonNull ListenableFuture<Pair<Uri, Long>> stopRecording() {
    Log.w(TAG, "stopRecording()");

    StopRecordingJob stopRecordingJob = new StopRecordingJob();

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(stopRecordingJob);

    return stopRecordingJob.getFuture();
  }

  private class StopRecordingJob extends Job {

    private final SettableFuture<Pair<Uri, Long>> future = new SettableFuture<>();

    public StopRecordingJob() {
      super(JobParameters.newBuilder()
                         .withGroupId(AudioRecorder.class.getSimpleName())
                         .create());
    }

    public ListenableFuture<Pair<Uri, Long>> getFuture() {
      return future;
    }

    @Override
    public void onAdded() {}

    @Override
    public void onRun() {
      if (mediaRecorder == null) {
        sendToFuture(new IOException("MediaRecorder was never initialized successfully!"));
        return;
      }

      try {
        mediaRecorder.stop();
      } catch (Exception e) {
        Log.w(TAG, e);
      }

      try {
        fd.close();
      } catch (IOException e) {
        Log.w("AudioRecorder", e);
      }

      mediaRecorder.release();
      mediaRecorder = null;

      try {
        long size = MediaUtil.getMediaSize(context, masterSecret, captureUri);
        sendToFuture(new Pair<>(captureUri, size));
      } catch (IOException ioe) {
        Log.w(TAG, ioe);
        sendToFuture(ioe);
      }

      captureUri = null;
      fd         = null;
    }

    @Override
    public boolean onShouldRetry(Exception e) {
      return false;
    }

    @Override
    public void onCanceled() {}

    private void sendToFuture(final @NonNull Pair<Uri, Long> result) {
      Util.runOnMain(new Runnable() {
        @Override
        public void run() {
          future.set(result);
        }
      });
    }

    private void sendToFuture(final @NonNull Exception exception) {
      Util.runOnMain(new Runnable() {
        @Override
        public void run() {
          future.setException(exception);
        }
      });
    }
  }

  private class StartRecordingJob extends Job {

    public StartRecordingJob() {
      super(JobParameters.newBuilder()
                         .withGroupId(AudioRecorder.class.getSimpleName())
                         .create());
    }

    @Override
    public void onAdded() {}

    @Override
    public void onRun() throws Exception {
      if (mediaRecorder != null) {
        throw new AssertionError("We can only record once at a time.");
      }

      ParcelFileDescriptor fds[] = ParcelFileDescriptor.createPipe();

      fd            = fds[1];
      captureUri    = blobProvider.create(masterSecret, new ParcelFileDescriptor.AutoCloseInputStream(fds[0]));
      mediaRecorder = new MediaRecorder();
      mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
      mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
      mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
      mediaRecorder.setOutputFile(fds[1].getFileDescriptor());

      mediaRecorder.prepare();

      try {
        mediaRecorder.start();
      } catch (Exception e) {
        Log.w(TAG, e);
        throw new IOException(e);
      }
    }

    @Override
    public boolean onShouldRetry(Exception e) {
      return false;
    }

    @Override
    public void onCanceled() {
      try {
        if (fd != null) {
          fd.close();
        }

        if (captureUri != null) {
          blobProvider.delete(captureUri);
        }

        fd            = null;
        mediaRecorder = null;
        captureUri    = null;
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }
  }

}
