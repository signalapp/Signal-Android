package org.thoughtcrime.securesms.jobmanager.impl;

import android.app.Application;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;
import org.thoughtcrime.securesms.util.ServiceUtil;

public class CellServiceConstraintObserver implements ConstraintObserver {

  private static final String TAG = Log.tag(CellServiceConstraintObserver.class);

  private static final String REASON = Log.tag(CellServiceConstraintObserver.class);

  private volatile Notifier     notifier;
  private volatile ServiceState lastKnownState;

  private static volatile CellServiceConstraintObserver instance;

  public static CellServiceConstraintObserver getInstance(@NonNull Application application) {
    if (instance == null) {
      synchronized (CellServiceConstraintObserver.class) {
        if (instance == null) {
          instance = new CellServiceConstraintObserver(application);
        }
      }
    }
    return instance;
  }

  private CellServiceConstraintObserver(@NonNull Application application) {
    TelephonyManager           telephonyManager     = ServiceUtil.getTelephonyManager(application);
    LegacyServiceStateListener serviceStateListener = new LegacyServiceStateListener();

    if (Build.VERSION.SDK_INT >= 31) {
      telephonyManager.registerTelephonyCallback(SignalExecutors.BOUNDED, new ServiceStateListenerApi31());
    } else {
      HandlerThread handlerThread = SignalExecutors.getAndStartHandlerThread("CellServiceConstraintObserver", Thread.NORM_PRIORITY);
      Handler       handler       = new Handler(handlerThread.getLooper());

      handler.post(() -> {
        telephonyManager.listen(serviceStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
      });
    }
  }

  @Override
  public void register(@NonNull Notifier notifier) {
    this.notifier = notifier;
  }

  public boolean hasService() {
    return lastKnownState != null && lastKnownState.getState() == ServiceState.STATE_IN_SERVICE;
  }

  @RequiresApi(31)
  private class ServiceStateListenerApi31 extends TelephonyCallback implements TelephonyCallback.ServiceStateListener {
    @Override
    public void onServiceStateChanged(@NonNull ServiceState serviceState) {
      lastKnownState = serviceState;

      if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
        Log.i(TAG, "[API " + Build.VERSION.SDK_INT + "] Cell service available.");

        if (notifier != null) {
          notifier.onConstraintMet(REASON);
        }
      } else {
        Log.w(TAG, "[API " + Build.VERSION.SDK_INT + "] Cell service unavailable. State: " + serviceState.getState());
      }
    }
  }

  private class LegacyServiceStateListener extends PhoneStateListener {

    LegacyServiceStateListener() {
      super();
    }

    @Override
    public void onServiceStateChanged(ServiceState serviceState) {
      lastKnownState = serviceState;

      if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
        Log.i(TAG, "[API " + Build.VERSION.SDK_INT + "] Cell service available.");

        if (notifier != null) {
          notifier.onConstraintMet(REASON);
        }
      } else {
        Log.w(TAG, "[API " + Build.VERSION.SDK_INT + "] Cell service unavailable. State: " + serviceState.getState());
      }
    }
  }
}
