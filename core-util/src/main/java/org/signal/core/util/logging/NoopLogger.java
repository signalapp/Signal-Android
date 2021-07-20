package org.signal.core.util.logging;

/**
 * A logger that does nothing.
 */
class NoopLogger extends Log.Logger {
  NoopLogger() {
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
