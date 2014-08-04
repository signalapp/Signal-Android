package org.whispersystems.jobqueue.requirements;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.whispersystems.jobqueue.dependencies.ContextDependent;

public class NetworkRequirement implements Requirement, ContextDependent {

  private transient Context context;

  public NetworkRequirement(Context context) {
    this.context = context;
  }

  public NetworkRequirement() {}

  @Override
  public boolean isPresent() {
    ConnectivityManager cm      = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo         netInfo = cm.getActiveNetworkInfo();

    return netInfo != null && netInfo.isConnectedOrConnecting();
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }
}
