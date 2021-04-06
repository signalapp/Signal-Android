package org.thoughtcrime.securesms.testutil;

import org.signal.core.util.logging.Log;

public final class SystemOutLogger extends Log.Logger {
  @Override
  public void v(String tag, String message, Throwable t) {
    printlnFormatted('v', tag, message, t);
  }

  @Override
  public void d(String tag, String message, Throwable t) {
    printlnFormatted('d', tag, message, t);
  }

  @Override
  public void i(String tag, String message, Throwable t) {
    printlnFormatted('i', tag, message, t);
  }

  @Override
  public void w(String tag, String message, Throwable t) {
    printlnFormatted('w', tag, message, t);
  }

  @Override
  public void e(String tag, String message, Throwable t) {
    printlnFormatted('e', tag, message, t);
  }

  @Override
  public void wtf(String tag, String message, Throwable t) {
    printlnFormatted('x', tag, message, t);
  }

  @Override
  public void blockUntilAllWritesFinished() { }

  private void printlnFormatted(char level, String tag, String message, Throwable t) {
    System.out.println(format(level, tag, message, t));
  }

  private String format(char level, String tag, String message, Throwable t) {
    if (t != null) {
      return String.format("%c[%s] %s %s:%s", level, tag, message, t.getClass().getSimpleName(), t.getMessage());
    } else {
      return String.format("%c[%s] %s", level, tag, message);
    }
  }
}
