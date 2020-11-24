/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;

public class Log {

  private Log() {}

  public static void v(String tag, String msg) {
    log(SignalProtocolLogger.VERBOSE, tag, msg);
  }

  public static void v(String tag, String msg, Throwable tr) {
    log(SignalProtocolLogger.VERBOSE, tag, msg + '\n' + getStackTraceString(tr));
  }

  public static void d(String tag, String msg) {
    log(SignalProtocolLogger.DEBUG, tag, msg);
  }

  public static void d(String tag, String msg, Throwable tr) {
    log(SignalProtocolLogger.DEBUG, tag, msg + '\n' + getStackTraceString(tr));
  }

  public static void i(String tag, String msg) {
    log(SignalProtocolLogger.INFO, tag, msg);
  }

  public static void i(String tag, String msg, Throwable tr) {
    log(SignalProtocolLogger.INFO, tag, msg + '\n' + getStackTraceString(tr));
  }

  public static void w(String tag, String msg) {
    log(SignalProtocolLogger.WARN, tag, msg);
  }

  public static void w(String tag, String msg, Throwable tr) {
    log(SignalProtocolLogger.WARN, tag, msg + '\n' + getStackTraceString(tr));
  }

  public static void w(String tag, Throwable tr) {
    log(SignalProtocolLogger.WARN, tag, getStackTraceString(tr));
  }

  public static void e(String tag, String msg) {
    log(SignalProtocolLogger.ERROR, tag, msg);
  }

  public static void e(String tag, String msg, Throwable tr) {
    log(SignalProtocolLogger.ERROR, tag, msg + '\n' + getStackTraceString(tr));
  }

  private static String getStackTraceString(Throwable tr) {
    if (tr == null) {
      return "";
    }

    // This is to reduce the amount of log spew that apps do in the non-error
    // condition of the network being unavailable.
    Throwable t = tr;
    while (t != null) {
      if (t instanceof UnknownHostException) {
        return "";
      }
      t = t.getCause();
    }

    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    tr.printStackTrace(pw);
    pw.flush();
    return sw.toString();
  }

  private static void log(int priority, String tag, String msg) {
    SignalProtocolLogger logger = SignalProtocolLoggerProvider.getProvider();

    if (logger != null) {
      logger.log(priority, tag, msg);
    }
  }


}
