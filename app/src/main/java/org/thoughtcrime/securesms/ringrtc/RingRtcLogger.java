package org.thoughtcrime.securesms.ringrtc;

import org.signal.core.util.logging.Log;

public class RingRtcLogger implements org.signal.ringrtc.Log.Logger {
  @Override
  public void v(String tag, String message, Throwable t) {
    Log.v(tag, message, t);
  }

  @Override
  public void d(String tag, String message, Throwable t) {
    Log.d(tag, message, t);
  }

  @Override
  public void i(String tag, String message, Throwable t) {
    Log.i(tag, message, t);
  }

  @Override
  public void w(String tag, String message, Throwable t) {
    Log.w(tag, message, t);
  }

  @Override
  public void e(String tag, String message, Throwable t) {
    Log.e(tag, message, t);
  }
}
