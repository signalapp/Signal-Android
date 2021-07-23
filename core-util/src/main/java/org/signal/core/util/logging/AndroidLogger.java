package org.signal.core.util.logging;

import android.annotation.SuppressLint;

@SuppressLint("LogNotSignal")
public final class AndroidLogger extends Log.Logger {

  @Override
  public void v(String tag, String message, Throwable t, boolean keepLonger) {
    android.util.Log.v(tag, message, t);
  }

  @Override
  public void d(String tag, String message, Throwable t, boolean keepLonger) {
    android.util.Log.d(tag, message, t);
  }

  @Override
  public void i(String tag, String message, Throwable t, boolean keepLonger) {
    android.util.Log.i(tag, message, t);
  }

  @Override
  public void w(String tag, String message, Throwable t, boolean keepLonger) {
    android.util.Log.w(tag, message, t);
  }

  @Override
  public void e(String tag, String message, Throwable t, boolean keepLonger) {
    android.util.Log.e(tag, message, t);
  }

  @Override
  public void flush() {
  }
}
