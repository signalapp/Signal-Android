/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.logging;

public interface SignalProtocolLogger {

  public static final int VERBOSE = 2;
  public static final int DEBUG   = 3;
  public static final int INFO    = 4;
  public static final int WARN    = 5;
  public static final int ERROR   = 6;
  public static final int ASSERT  = 7;

  public void log(int priority, String tag, String message);
}
