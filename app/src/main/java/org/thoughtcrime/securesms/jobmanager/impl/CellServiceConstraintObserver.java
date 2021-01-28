package org.thoughtcrime.securesms.jobmanager.impl;

import android.app.Application;
import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;

public class CellServiceConstraintObserver implements ConstraintObserver {

  private static final String REASON = CellServiceConstraintObserver.class.getSimpleName();

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
    TelephonyManager     telephonyManager     = (TelephonyManager) application.getSystemService(Context.TELEPHONY_SERVICE);
    ServiceStateListener serviceStateListener = new ServiceStateListener();

    SignalExecutors.BOUNDED.execute(() -> {
      telephonyManager.listen(serviceStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
    });
  }

  @Override
  public void register(@NonNull Notifier notifier) {
    this.notifier = notifier;
  }

  public boolean hasService() {
    return lastKnownState != null && lastKnownState.getState() == ServiceState.STATE_IN_SERVICE;
  }

  private class ServiceStateListener extends PhoneStateListener {
    @Override
    public void onServiceStateChanged(ServiceState serviceState) {
      lastKnownState = serviceState;

      if (serviceState.getState() == ServiceState.STATE_IN_SERVICE && notifier != null) {
        notifier.onConstraintMet(REASON);
      }
    }
  }
}
