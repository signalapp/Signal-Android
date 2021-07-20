package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mobilecoin.lib.log.LogAdapter;
import com.mobilecoin.lib.log.Logger;

import org.signal.core.util.logging.Log;

final class MobileCoinLogAdapter implements LogAdapter {

  @Override
  public boolean isLoggable(@NonNull Logger.Level level, @NonNull String s) {
    return level.ordinal() >= Logger.Level.WARNING.ordinal();
  }

  /**
   * @param metadata May contain PII, do not log.
   */
  @Override
  public void log(@NonNull Logger.Level level,
                  @NonNull String tag,
                  @NonNull String message,
                  @Nullable Throwable throwable,
                  @NonNull Object... metadata)
  {
    switch (level) {
      case INFO:
        Log.i(tag, message, throwable);
        break;
      case VERBOSE:
        Log.v(tag, message, throwable);
        break;
      case DEBUG:
        Log.d(tag, message, throwable);
        break;
      case WARNING:
        Log.w(tag, message, throwable);
        break;
      case ERROR:
      case WTF:
        Log.e(tag, message, throwable);
        break;
    }
  }
}
