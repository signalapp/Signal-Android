package org.signal.core.util.logging;

import androidx.annotation.NonNull;

/**
 * A way to treat N loggers as one. Wraps a bunch of other loggers and forwards the method calls to
 * all of them.
 */
class CompoundLogger extends Log.Logger {

  private final Log.Logger[] loggers;

  CompoundLogger(@NonNull Log.Logger... loggers) {
    this.loggers = loggers;
  }

  @Override
  public void v(String tag, String message, Throwable t, boolean keepLonger) {
    for (Log.Logger logger : loggers) {
      logger.v(tag, message, t, keepLonger);
    }
  }

  @Override
  public void d(String tag, String message, Throwable t, boolean keepLonger) {
    for (Log.Logger logger : loggers) {
      logger.d(tag, message, t, keepLonger);
    }
  }

  @Override
  public void i(String tag, String message, Throwable t, boolean keepLonger) {
    for (Log.Logger logger : loggers) {
      logger.i(tag, message, t, keepLonger);
    }
  }

  @Override
  public void w(String tag, String message, Throwable t, boolean keepLonger) {
    for (Log.Logger logger : loggers) {
      logger.w(tag, message, t, keepLonger);
    }
  }

  @Override
  public void e(String tag, String message, Throwable t, boolean keepLonger) {
    for (Log.Logger logger : loggers) {
      logger.e(tag, message, t, keepLonger);
    }
  }

  @Override
  public void v(String tag, String message, Throwable t) {
    for (Log.Logger logger :loggers) {
      logger.v(tag, message, t);
    }
  }

  @Override
  public void d(String tag, String message, Throwable t) {
    for (Log.Logger logger :loggers) {
      logger.d(tag, message, t);
    }
  }

  @Override
  public void i(String tag, String message, Throwable t) {
    for (Log.Logger logger :loggers) {
      logger.i(tag, message, t);
    }
  }

  @Override
  public void w(String tag, String message, Throwable t) {
    for (Log.Logger logger :loggers) {
      logger.w(tag, message, t);
    }
  }

  @Override
  public void e(String tag, String message, Throwable t) {
    for (Log.Logger logger :loggers) {
      logger.e(tag, message, t);
    }
  }

  @Override
  public void v(String tag, String message) {
    for (Log.Logger logger :loggers) {
      logger.v(tag, message);
    }
  }

  @Override
  public void d(String tag, String message) {
    for (Log.Logger logger :loggers) {
      logger.d(tag, message);
    }
  }

  @Override
  public void i(String tag, String message) {
    for (Log.Logger logger :loggers) {
      logger.i(tag, message);
    }
  }

  @Override
  public void w(String tag, String message) {
    for (Log.Logger logger :loggers) {
      logger.w(tag, message);
    }
  }

  @Override
  public void e(String tag, String message) {
    for (Log.Logger logger :loggers) {
      logger.e(tag, message);
    }
  }

  @Override
  public void flush() {
    for (Log.Logger logger : loggers) {
      logger.flush();
    }
  }
}
