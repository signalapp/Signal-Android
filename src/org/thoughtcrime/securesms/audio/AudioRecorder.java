package org.thoughtcrime.securesms.audio;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ThreadUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import ws.com.google.android.mms.ContentType;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class AudioRecorder {

  private static final String TAG = AudioRecorder.class.getSimpleName();

  private static final ExecutorService executor = ThreadUtil.newDynamicSingleThreadedExecutor();

  private final Context                context;
  private final MasterSecret           masterSecret;
  private final PersistentBlobProvider blobProvider;

  private AudioCodec audioCodec;
  private Uri        captureUri;

  public AudioRecorder(@NonNull Context context, @NonNull MasterSecret masterSecret) {
    this.context      = context;
    this.masterSecret = masterSecret;
    this.blobProvider = PersistentBlobProvider.getInstance(context.getApplicationContext());
  }

  public void startRecording() {
    Log.w(TAG, "startRecording()");

    executor.execute(new Runnable() {
      @Override
      public void run() {
        Log.w(TAG, "Running startRecording() + " + Thread.currentThread().getId());
        try {
          if (audioCodec != null) {
            throw new AssertionError("We can only record once at a time.");
          }

          ParcelFileDescriptor fds[] = ParcelFileDescriptor.createPipe();

          captureUri  = blobProvider.create(masterSecret,
                                            new ParcelFileDescriptor.AutoCloseInputStream(fds[0]),
                                            ContentType.AUDIO_AAC);
          audioCodec  = new AudioCodec();

          audioCodec.start(new ParcelFileDescriptor.AutoCloseOutputStream(fds[1]));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    });
  }

  public @NonNull ListenableFuture<Pair<Uri, Long>> stopRecording() {
    Log.w(TAG, "stopRecording()");

    final SettableFuture<Pair<Uri, Long>> future = new SettableFuture<>();

    executor.execute(new Runnable() {
      @Override
      public void run() {
        if (audioCodec == null) {
          sendToFuture(future, new IOException("MediaRecorder was never initialized successfully!"));
          return;
        }

        audioCodec.stop();

        try {
          long size = MediaUtil.getMediaSize(context, masterSecret, captureUri);
          sendToFuture(future, new Pair<>(captureUri, size));
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
          sendToFuture(future, ioe);
        }

        audioCodec = null;
        captureUri = null;
      }
    });

    return future;
  }

  private <T> void sendToFuture(final SettableFuture<T> future, final Exception exception) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        future.setException(exception);
      }
    });
  }

  private <T> void sendToFuture(final SettableFuture<T> future, final T result) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        future.set(result);
      }
    });
  }
}
