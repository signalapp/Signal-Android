package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import org.signal.core.util.ExceptionUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.io.IOException;

import javax.net.ssl.SSLException;

import io.reactivex.rxjava3.exceptions.OnErrorNotImplementedException;

public class SignalUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

  private static final String TAG = Log.tag(SignalUncaughtExceptionHandler.class);

  private final Thread.UncaughtExceptionHandler originalHandler;

  public SignalUncaughtExceptionHandler(@NonNull Thread.UncaughtExceptionHandler originalHandler) {
    this.originalHandler = originalHandler;
  }

  @Override
  public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
    // Seeing weird situations where SSLExceptions aren't being caught as IOExceptions
    if (e instanceof SSLException) {
      if (e instanceof IOException) {
        Log.w(TAG, "Uncaught SSLException! It *is* an IOException!", e);
      } else {
        Log.w(TAG, "Uncaught SSLException! It is *not* an IOException!", e);
      }
      return;
    }

    if (e instanceof OnErrorNotImplementedException && e.getCause() != null) {
      e = e.getCause();
    }

    Log.e(TAG, "", e, true);
    SignalStore.blockUntilAllWritesFinished();
    Log.blockUntilAllWritesFinished();
    ApplicationDependencies.getJobManager().flush();
    originalHandler.uncaughtException(t, ExceptionUtil.joinStackTraceAndMessage(e));
  }
}
