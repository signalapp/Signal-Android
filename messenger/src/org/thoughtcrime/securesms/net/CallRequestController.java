package org.thoughtcrime.securesms.net;

import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.InputStream;

import okhttp3.Call;

public class CallRequestController implements RequestController {

  private final Call call;

  private InputStream  stream;
  private boolean      canceled;

  public CallRequestController(@NonNull Call call) {
    this.call = call;
  }

  @Override
  public void cancel() {
    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
      synchronized (CallRequestController.this) {
        if (canceled) return;
        
        call.cancel();

        if (stream != null) {
          Util.close(stream);
        }

        canceled = true;
      }
    });
  }

  public synchronized void setStream(@NonNull InputStream stream) {
    if (canceled) {
      Util.close(stream);
    } else {
      this.stream = stream;
    }
    notifyAll();
  }

  /**
   * Blocks until the stream is available or until the request is canceled.
   */
  @WorkerThread
  public synchronized Optional<InputStream> getStream() {
    while(stream == null && !canceled) {
      Util.wait(this, 0);
    }

    return Optional.fromNullable(this.stream);
  }
}
