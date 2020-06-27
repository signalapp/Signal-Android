package org.thoughtcrime.securesms.mediasend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.net.ConnectivityManagerCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.thoughtcrime.securesms.util.ServiceUtil;

/**
 * Lifecycle-bound observer for whether or not the active network connection is metered.
 */
class MeteredConnectivityObserver extends BroadcastReceiver implements DefaultLifecycleObserver {

  private final Context                  context;
  private final ConnectivityManager      connectivityManager;
  private final MutableLiveData<Boolean> metered;

  @MainThread
  MeteredConnectivityObserver(@NonNull Context context, @NonNull LifecycleOwner lifecycleOwner) {
    this.context             = context;
    this.connectivityManager = ServiceUtil.getConnectivityManager(context);
    this.metered             = new MutableLiveData<>();

    this.metered.setValue(ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager));
    lifecycleOwner.getLifecycle().addObserver(this);
  }

  @Override
  public void onCreate(@NonNull LifecycleOwner owner) {
    context.registerReceiver(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
  }

  @Override
  public void onDestroy(@NonNull LifecycleOwner owner) {
    context.unregisterReceiver(this);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    metered.postValue(ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager));
  }

  /**
   * @return An observable value that is false when the network is unmetered, and true if the
   *         network is either metered or unavailable.
   */
  @NonNull LiveData<Boolean> isMetered() {
    return metered;
  }
}
