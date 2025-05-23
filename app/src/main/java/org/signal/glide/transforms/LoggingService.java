package org.signal.glide.transforms;

import androidx.annotation.Nullable;

public interface LoggingService {
  void logDebug(LogTag tag, LogMessage message);
  void logInfo(LogTag tag, LogMessage message);
  void logWarn(LogTag tag, LogMessage message);
  void logError(LogTag tag, LogMessage message, @Nullable Throwable throwable);
}
