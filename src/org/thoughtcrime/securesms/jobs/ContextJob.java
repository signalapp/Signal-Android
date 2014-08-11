package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.Params;

public abstract class ContextJob extends Job {

  transient protected Context context;

  protected ContextJob(Params params) {
    super(params);
  }

  public void setContext(Context context) {
    this.context = context;
  }

  protected Context getContext() {
    return context;
  }
}
