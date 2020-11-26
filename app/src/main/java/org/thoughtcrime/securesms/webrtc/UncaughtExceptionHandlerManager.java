package org.thoughtcrime.securesms.webrtc;

import org.thoughtcrime.securesms.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows multiple default uncaught exception handlers to be registered
 *
 * Calls all registered handlers in reverse order of registration.
 * Errors in one handler do not prevent subsequent handlers from being called.
 */
public class UncaughtExceptionHandlerManager implements Thread.UncaughtExceptionHandler {
  private final Thread.UncaughtExceptionHandler originalHandler;
  private final List<Thread.UncaughtExceptionHandler> handlers = new ArrayList<Thread.UncaughtExceptionHandler>();

  public UncaughtExceptionHandlerManager() {
    originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    registerHandler(originalHandler);
    Thread.setDefaultUncaughtExceptionHandler(this);
  }

  public void registerHandler(Thread.UncaughtExceptionHandler handler) {
    handlers.add(handler);
  }

  public void unregister() {
    Thread.setDefaultUncaughtExceptionHandler(originalHandler);
  }

  @Override
  public void uncaughtException(Thread thread, Throwable throwable) {
    for (int i = handlers.size() - 1; i >= 0; i--) {
      try {
        handlers.get(i).uncaughtException(thread, throwable);
      } catch(Throwable t) {
        Log.e("UncaughtExceptionHandlerManager", "Error in uncaught exception handling", t);
      }
    }
  }
}
