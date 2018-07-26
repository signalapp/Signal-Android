package org.thoughtcrime.securesms.logging;

import android.support.annotation.MainThread;

public class Log {

  private static Logger[] loggers;

  @MainThread
  public static void initialize(Logger... loggers) {
    Log.loggers = loggers;
  }

  public static void v(String tag, String message) {
    v(tag, message, null);
  }

  public static void d(String tag, String message) {
    d(tag, message, null);
  }

  public static void i(String tag, String message) {
    i(tag, message, null);
  }

  public static void w(String tag, String message) {
    w(tag, message, null);
  }

  public static void e(String tag, String message) {
    e(tag, message, null);
  }

  public static void wtf(String tag, String message) {
    wtf(tag, message, null);
  }

  public static void v(String tag, Throwable t) {
    v(tag, null, t);
  }

  public static void d(String tag, Throwable t) {
    d(tag, null, t);
  }

  public static void i(String tag, Throwable t) {
    i(tag, null, t);
  }

  public static void w(String tag, Throwable t) {
    w(tag, null, t);
  }

  public static void e(String tag, Throwable t) {
    e(tag, null, t);
  }

  public static void wtf(String tag, Throwable t) {
    wtf(tag, null, t);
  }

  public static void v(String tag, String message, Throwable t) {
    if (loggers != null) {
      for (Logger logger : loggers) {
        logger.v(tag, message, t);
      }
    } else {
      android.util.Log.v(tag, message, t);
    }
  }

  public static void d(String tag, String message, Throwable t) {
    if (loggers != null) {
      for (Logger logger : loggers) {
        logger.d(tag, message, t);
      }
    } else {
      android.util.Log.d(tag, message, t);
    }
  }

  public static void i(String tag, String message, Throwable t) {
    if (loggers != null) {
      for (Logger logger : loggers) {
        logger.i(tag, message, t);
      }
    } else {
      android.util.Log.i(tag, message, t);
    }
  }

  public static void w(String tag, String message, Throwable t) {
    if (loggers != null) {
      for (Logger logger : loggers) {
        logger.w(tag, message, t);
      }
    } else {
      android.util.Log.w(tag, message, t);
    }
  }

  public static void e(String tag, String message, Throwable t) {
    if (loggers != null) {
      for (Logger logger : loggers) {
        logger.e(tag, message, t);
      }
    } else {
      android.util.Log.e(tag, message, t);
    }
  }

  public static void wtf(String tag, String message, Throwable t) {
    if (loggers != null) {
      for (Logger logger : loggers) {
        logger.wtf(tag, message, t);
      }
    } else {
      android.util.Log.wtf(tag, message, t);
    }
  }


  public static abstract class Logger {
    public abstract void v(String tag, String message, Throwable t);
    public abstract void d(String tag, String message, Throwable t);
    public abstract void i(String tag, String message, Throwable t);
    public abstract void w(String tag, String message, Throwable t);
    public abstract void e(String tag, String message, Throwable t);
    public abstract void wtf(String tag, String message, Throwable t);
  }
}
