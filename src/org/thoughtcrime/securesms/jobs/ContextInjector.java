package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.di.DependencyInjector;

public class ContextInjector implements DependencyInjector {

  private final Context context;

  public ContextInjector(Context context) {
    this.context = context;
  }

  @Override
  public void inject(BaseJob job) {
    if (job instanceof ContextJob) {
      ((ContextJob)job).setContext(context);
    }
  }
}
