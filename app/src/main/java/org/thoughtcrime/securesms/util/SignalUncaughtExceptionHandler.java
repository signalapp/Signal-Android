package org.thoughtcrime.securesms.util;

import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;

import org.signal.core.util.ExceptionUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.LogDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
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

    if (e instanceof SQLiteDatabaseCorruptException) {
      if (e.getMessage() != null && e.getMessage().contains("message_fts")) {
        Log.w(TAG, "FTS corrupted! Resetting FTS index.");
        SignalDatabase.messageSearch().fullyResetTables();
      } else {
        Log.w(TAG, "Some non-FTS related corruption?");
      }
    }

    if (e instanceof SQLiteException && e.getMessage() != null) {
      if (e.getMessage().contains("invalid fts5 file format")) {
        Log.w(TAG, "FTS in invalid state! Resetting FTS index.");
        SignalDatabase.messageSearch().fullyResetTables();
      } else if (e.getMessage().contains("no such table: message_fts")) {
        Log.w(TAG, "FTS table not found! Resetting FTS index.");
        SignalDatabase.messageSearch().fullyResetTables();
      }
    }

    if (e instanceof OnErrorNotImplementedException && e.getCause() != null) {
      e = e.getCause();
    }

    String exceptionName = e.getClass().getCanonicalName();
    if (exceptionName == null) {
      exceptionName = e.getClass().getName();
    }

    Log.e(TAG, "", e, true);
    LogDatabase.getInstance(AppDependencies.getApplication()).crashes().saveCrash(System.currentTimeMillis(), exceptionName, e.getMessage(), ExceptionUtil.convertThrowableToString(e));
    SignalStore.blockUntilAllWritesFinished();
    Log.blockUntilAllWritesFinished();
    AppDependencies.getJobManager().flush();
    originalHandler.uncaughtException(t, ExceptionUtil.joinStackTraceAndMessage(e));
  }
}
