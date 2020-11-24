package org.thoughtcrime.securesms.jobmanager.impl;

import android.app.Application;
import android.content.Context;
import androidx.annotation.NonNull;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;

public class CellServiceConstraintObserver implements ConstraintObserver {

  private static final String REASON = CellServiceConstraintObserver.class.getSimpleName();

  private Notifier notifier;

  public CellServiceConstraintObserver(@NonNull Application application) {
    TelephonyManager     telephonyManager     = (TelephonyManager) application.getSystemService(Context.TELEPHONY_SERVICE);
    ServiceStateListener serviceStateListener = new ServiceStateListener();

    telephonyManager.listen(serviceStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
  }

  @Override
  public void register(@NonNull Notifier notifier) {
    this.notifier = notifier;
  }

  private class ServiceStateListener extends PhoneStateListener {
    @Override
    public void onServiceStateChanged(ServiceState serviceState) {
      if (serviceState.getState() == ServiceState.STATE_IN_SERVICE && notifier != null) {
        notifier.onConstraintMet(REASON);
      }
    }
  }
}
