package org.thoughtcrime.securesms.testutil;

import org.signal.core.util.logging.Log;

public class EmptyLogger extends Log.Logger {
  public EmptyLogger() {
    super(0);
  }

  @Override
  public void v(String tag, String message, Throwable t, long duration) { }

  @Override
  public void d(String tag, String message, Throwable t, long duration) { }

  @Override
  public void i(String tag, String message, Throwable t, long duration) { }

  @Override
  public void w(String tag, String message, Throwable t, long duration) { }

  @Override
  public void e(String tag, String message, Throwable t, long duration) { }

  @Override
  public void flush() { }
}
