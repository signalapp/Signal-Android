package org.thoughtcrime.securesms.audio;

import android.content.Context;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;

import java.io.IOException;

public class AudioRecorder {

  private static final String TAG = AudioRecorder.class.getSimpleName();

  private final MasterSecret           masterSecret;
  private final PersistentBlobProvider blobProvider;

  private MediaRecorder        mediaRecorder;
  private Uri                  captureUri;
  private ParcelFileDescriptor fd;

  public AudioRecorder(@NonNull Context context, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    this.blobProvider = PersistentBlobProvider.getInstance(context.getApplicationContext());
  }

  public void startRecording() throws IOException {
    Log.w(TAG, "startRecording()");

    if (this.mediaRecorder != null) {
      throw new AssertionError("We can only record once at a time.");
    }

    ParcelFileDescriptor fds[] = ParcelFileDescriptor.createPipe();

    this.fd            = fds[1];
    this.captureUri    = blobProvider.create(masterSecret, new ParcelFileDescriptor.AutoCloseInputStream(fds[0]));
    this.mediaRecorder = new MediaRecorder();
    this.mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    this.mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
    this.mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
    this.mediaRecorder.setOutputFile(fds[1].getFileDescriptor());

    this.mediaRecorder.prepare();

    try {
      this.mediaRecorder.start();
    } catch (Exception e) {
      this.fd.close();
      this.blobProvider.delete(this.captureUri);
      throw new IOException(e);
    }
  }

  public @Nullable Uri stopRecording() {
    Log.w(TAG, "stopRecording()");

    if (this.mediaRecorder == null) return null;

    try {
      this.mediaRecorder.stop();
    } catch (Exception e) {
      Log.w(TAG, e);
    }

    this.mediaRecorder.release();
    this.mediaRecorder = null;

    try {
      this.fd.close();
    } catch (IOException e) {
      Log.w("AudioRecorder", e);
    }

    return captureUri;
  }

}
