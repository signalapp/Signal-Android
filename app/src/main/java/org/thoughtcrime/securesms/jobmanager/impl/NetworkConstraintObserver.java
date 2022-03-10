package org.thoughtcrime.securesms.jobmanager.impl;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class NetworkConstraintObserver implements ConstraintObserver {

  private static final String REASON = Log.tag(NetworkConstraintObserver.class);
  private static final String TAG    = Log.tag(NetworkConstraintObserver.class);

  private final Application application;

  private volatile Notifier notifier;
  private volatile boolean  hasInternet;

  private final Set<NetworkListener> networkListeners = new HashSet<>();

  private static volatile NetworkConstraintObserver instance;

  public static NetworkConstraintObserver getInstance(@NonNull Application application) {
    if (instance == null) {
      synchronized (NetworkConstraintObserver.class) {
        if (instance == null) {
          instance = new NetworkConstraintObserver(application);
        }
      }
    }
    return instance;
  }

  private NetworkConstraintObserver(Application application) {
    this.application = application;
  }

  @Override
  public void register(@NonNull Notifier notifier) {
    this.notifier    = notifier;
    this.hasInternet = isActiveNetworkConnected(application);

    requestNetwork(0);
  }

  @TargetApi(19)
  private static boolean isActiveNetworkConnected(@NonNull Context context) {
    ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo         activeNetworkInfo   = connectivityManager.getActiveNetworkInfo();

    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
  }

  private void requestNetwork(int retryCount) {
    optimisticallyUpdateNetworkState();

    if (Build.VERSION.SDK_INT < 24 || retryCount > 5) {
      hasInternet = isActiveNetworkConnected(application);

      application.registerReceiver(new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          hasInternet = isActiveNetworkConnected(context);

          if (hasInternet) {
            Log.i(TAG, logPrefix() + "Network available.");
            notifier.onConstraintMet(REASON);
          } else {
            Log.w(TAG, logPrefix() + "Network unavailable.");
          }

          notifyListeners();
        }
      }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    } else {
      NetworkRequest request = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                                           .build();

      ConnectivityManager connectivityManager = Objects.requireNonNull(ContextCompat.getSystemService(application, ConnectivityManager.class));

      if (Build.VERSION.SDK_INT >= 26) {
        connectivityManager.requestNetwork(request, new NetworkStateListener26(retryCount), 1000);
      } else {
        connectivityManager.requestNetwork(request, new NetworkStateListener24());
      }
    }
  }

  public boolean hasInternet() {
    return hasInternet;
  }

  public void addListener(@Nullable NetworkListener networkListener) {
    synchronized (networkListeners) {
      networkListeners.add(networkListener);
    }
  }

  public void removeListener(@Nullable NetworkListener networkListener) {
    if (networkListener == null) {
      return;
    }

    synchronized (networkListeners) {
      networkListeners.remove(networkListener);
    }
  }

  /**
   * The newer API methods are occasionally unreliable. This lets us assume the best case scenario, by using both new and old methods and taking the most
   * optimistic result.
   */
  private void optimisticallyUpdateNetworkState() {
    final boolean currentState = hasInternet;
    final boolean newState     = isActiveNetworkConnected(application);

    if (newState && !currentState) {
      Log.w(TAG, logPrefix() + "isActiveNetworkConnected() thinks we're connected, but other methods indicate we're not. Assuming we have internet and notifying listeners.");
      this.hasInternet = newState;
      notifier.onConstraintMet(REASON);
      notifyListeners();
    }
  }

  private void notifyListeners() {
    synchronized (networkListeners) {
      //noinspection SimplifyStreamApiCallChains
      networkListeners.stream().forEach(NetworkListener::onNetworkChanged);
    }
  }

  private static String logPrefix() {
    return "[API " + Build.VERSION.SDK_INT + "] ";
  }

  @TargetApi(24)
  private class NetworkStateListener24 extends ConnectivityManager.NetworkCallback {
    @Override
    public void onAvailable(@NonNull Network network) {
      Log.i(TAG, logPrefix() + "Network available. " + network.hashCode());
      hasInternet = true;
      notifier.onConstraintMet(REASON);
      notifyListeners();
    }

    @Override
    public void onLost(@NonNull Network network) {
      Log.w(TAG, logPrefix() + "Network unavailable. " + network.hashCode());
      hasInternet = false;
      notifyListeners();
    }
  }

  @TargetApi(26)
  private class NetworkStateListener26 extends NetworkStateListener24 {
    private final int  retryCount;
    private final long createTime = System.currentTimeMillis();

    public NetworkStateListener26(int retryCount) {
      this.retryCount = retryCount;
    }

    @Override
    public void onUnavailable() {
      Log.w(TAG, logPrefix() + "No networks available or timeout hit. Retry count: " + retryCount + ", Time since creation: " + (System.currentTimeMillis() - createTime) + " ms");
      requestNetwork(retryCount + 1);
    }
  }

  public interface NetworkListener {
    void onNetworkChanged();
  }
}
