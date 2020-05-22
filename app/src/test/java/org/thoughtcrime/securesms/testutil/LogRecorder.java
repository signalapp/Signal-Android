package org.thoughtcrime.securesms.testutil;

import org.thoughtcrime.securesms.logging.Log;

import java.util.ArrayList;
import java.util.List;

public final class LogRecorder extends Log.Logger {

  private final List<Entry> verbose     = new ArrayList<>();
  private final List<Entry> debug       = new ArrayList<>();
  private final List<Entry> information = new ArrayList<>();
  private final List<Entry> warnings    = new ArrayList<>();
  private final List<Entry> errors      = new ArrayList<>();
  private final List<Entry> wtf         = new ArrayList<>();

  @Override
  public void v(String tag, String message, Throwable t) {
    verbose.add(new Entry(tag, message, t));
  }

  @Override
  public void d(String tag, String message, Throwable t) {
    debug.add(new Entry(tag, message, t));
  }

  @Override
  public void i(String tag, String message, Throwable t) {
    information.add(new Entry(tag, message, t));
  }

  @Override
  public void w(String tag, String message, Throwable t) {
    warnings.add(new Entry(tag, message, t));
  }

  @Override
  public void e(String tag, String message, Throwable t) {
    errors.add(new Entry(tag, message, t));
  }

  @Override
  public void wtf(String tag, String message, Throwable t) {
    wtf.add(new Entry(tag, message, t));
  }

  @Override
  public void blockUntilAllWritesFinished() {
  }

  public List<Entry> getVerbose() {
    return verbose;
  }

  public List<Entry> getDebug() {
    return debug;
  }

  public List<Entry> getInformation() {
    return information;
  }

  public List<Entry> getWarnings() {
    return warnings;
  }

  public List<Entry> getErrors() {
    return errors;
  }

  public List<Entry> getWtf() {
    return wtf;
  }

  public static final class Entry {
    private final String    tag;
    private final String    message;
    private final Throwable throwable;

    private Entry(String tag, String message, Throwable throwable) {
      this.tag       = tag;
      this.message   = message;
      this.throwable = throwable;
    }

    public String getTag() {
      return tag;
    }

    public String getMessage() {
      return message;
    }

    public Throwable getThrowable() {
      return throwable;
    }
  }
}
