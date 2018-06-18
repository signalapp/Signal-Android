package org.thoughtcrime.securesms.jobs.requirements;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import org.thoughtcrime.securesms.jobmanager.requirements.RequirementListener;
import org.thoughtcrime.securesms.jobmanager.requirements.RequirementProvider;

import java.util.concurrent.atomic.AtomicBoolean;

public class ServiceRequirementProvider implements RequirementProvider {

  private final TelephonyManager     telephonyManager;
  private final ServiceStateListener serviceStateListener;
  private final AtomicBoolean        listeningForServiceState;

  private RequirementListener requirementListener;

  public ServiceRequirementProvider(Context context) {
    this.telephonyManager         = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    this.serviceStateListener     = new ServiceStateListener();
    this.listeningForServiceState = new AtomicBoolean(false);
  }

  @Override
  public void setListener(RequirementListener requirementListener) {
    this.requirementListener = requirementListener;
  }

  public void start() {
    if (listeningForServiceState.compareAndSet(false, true)) {
      this.telephonyManager.listen(serviceStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
    }
  }

  private void handleInService() {
    if (listeningForServiceState.compareAndSet(true, false)) {
      this.telephonyManager.listen(serviceStateListener, PhoneStateListener.LISTEN_NONE);
    }

    if (requirementListener != null) {
      requirementListener.onRequirementStatusChanged();
    }
  }

  private class ServiceStateListener extends PhoneStateListener {
    @Override
    public void onServiceStateChanged(ServiceState serviceState) {
      if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
        handleInService();
      }
    }
  }
}
