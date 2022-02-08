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
    this.notifier = notifier;
    requestNetwork(0);
  }

  @TargetApi(19)
  private static boolean isActiveNetworkConnected(@NonNull Context context) {
    ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo         activeNetworkInfo   = connectivityManager.getActiveNetworkInfo();

    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
  }

  private void requestNetwork(int retryCount) {
    if (Build.VERSION.SDK_INT < 24 || retryCount > 5) {
      hasInternet = isActiveNetworkConnected(application);

      application.registerReceiver(new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          hasInternet = isActiveNetworkConnected(context);

          if (hasInternet) {
            notifier.onConstraintMet(REASON);
          }
          notifyListeners();
        }
      }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    } else {
      NetworkRequest request = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                                           .build();

      ConnectivityManager connectivityManager = Objects.requireNonNull(ContextCompat.getSystemService(application, ConnectivityManager.class));
      connectivityManager.requestNetwork(request, Build.VERSION.SDK_INT >= 26 ? new NetworkStateListener26(retryCount) : new NetworkStateListener24());
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

  private void notifyListeners() {
    synchronized (networkListeners) {
      //noinspection SimplifyStreamApiCallChains
      networkListeners.stream().forEach(NetworkListener::onNetworkChanged);
    }
  }

  @TargetApi(24)
  private class NetworkStateListener24 extends ConnectivityManager.NetworkCallback {
    @Override
    public void onAvailable(@NonNull Network network) {
      Log.i(REASON, "Network available: " + network.hashCode());
      hasInternet = true;
      notifier.onConstraintMet(REASON);
      notifyListeners();
    }

    @Override
    public void onLost(@NonNull Network network) {
      Log.i(REASON, "Network loss: " + network.hashCode());
      hasInternet = false;
      notifyListeners();
    }
  }

  @TargetApi(26)
  private class NetworkStateListener26 extends NetworkStateListener24 {
    private final int retryCount;

    public NetworkStateListener26(int retryCount) {
      this.retryCount = retryCount;
    }

    @Override
    public void onUnavailable() {
      Log.w(REASON, "No networks available");
      requestNetwork(retryCount + 1);
    }
  }

  public interface NetworkListener {
    void onNetworkChanged();
  }
}
