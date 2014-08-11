package org.thoughtcrime.securesms.jobs;

import android.util.Log;

import com.path.android.jobqueue.log.CustomLogger;

public class JobLogger implements CustomLogger {
  @Override
  public boolean isDebugEnabled() {
    return false;
  }

  @Override
  public void d(String text, Object... args) {
    Log.w("JobManager", String.format(text, args));
  }

  @Override
  public void e(Throwable t, String text, Object... args) {
    Log.w("JobManager", String.format(text, args), t);
  }

  @Override
  public void e(String text, Object... args) {
    Log.w("JobManager", String.format(text, args));
  }
}
