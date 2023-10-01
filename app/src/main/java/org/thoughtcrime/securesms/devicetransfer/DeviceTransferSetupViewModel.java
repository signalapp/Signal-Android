package org.thoughtcrime.securesms.devicetransfer;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import org.signal.core.util.logging.Log;
import org.signal.devicetransfer.TransferStatus;
import org.signal.devicetransfer.WifiDirect;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.util.livedata.Store;

/**
 * Drives and wraps the state of the transfer setup process.
 */
public final class DeviceTransferSetupViewModel extends ViewModel {

  private static final String TAG = Log.tag(DeviceTransferSetupViewModel.class);

  private final Store<DeviceSetupState>    store;
  private final LiveData<DeviceSetupState> distinctStepChanges;

  private boolean shutdown;

  public DeviceTransferSetupViewModel() {
    this.store               = new Store<>(new DeviceSetupState());
    this.distinctStepChanges = LiveDataUtil.distinctUntilChanged(this.store.getStateLiveData(), (current, next) -> current.getCurrentSetupStep() == next.getCurrentSetupStep());
  }

  public @NonNull LiveData<DeviceSetupState> getState() {
    return distinctStepChanges;
  }

  public boolean isNotShutdown() {
    return !shutdown;
  }

  public void onTransferEvent(@NonNull TransferStatus event) {
    if (shutdown) {
      return;
    }

    Log.i(TAG, "Handling transferStatus: " + event.getTransferMode());
    switch (event.getTransferMode()) {
      case UNAVAILABLE:
      case NETWORK_CONNECTED:
        Log.d(TAG, "Ignore event: " + event.getTransferMode());
        break;
      case READY:
      case STARTING_UP:
        store.update(s -> s.updateStep(SetupStep.SETTING_UP));
        break;
      case DISCOVERY:
        store.update(s -> s.updateStep(SetupStep.WAITING));
        break;
      case VERIFICATION_REQUIRED:
        store.update(s -> s.updateVerificationRequired(event.getAuthenticationCode()));
        break;
      case SERVICE_CONNECTED:
        store.update(s -> s.updateStep(SetupStep.CONNECTED));
        break;
      case SHUTDOWN:
      case FAILED:
        store.update(s -> s.updateStep(SetupStep.ERROR));
        break;
    }
  }

  public void onLocationPermissionDenied() {
    Log.i(TAG, "Location permissions denied");
    store.update(s -> s.updateStep(SetupStep.PERMISSIONS_DENIED));
  }

  public void onWifiDisabled(boolean wifiManagerNotAvailable) {
    Log.i(TAG, "Wifi disabled manager: " + wifiManagerNotAvailable);
    store.update(s -> s.updateStep(SetupStep.WIFI_DISABLED));
  }

  public void onWifiDirectUnavailable(WifiDirect.AvailableStatus availability) {
    Log.i(TAG, "Wifi Direct unavailable: " + availability);
    if (availability == WifiDirect.AvailableStatus.REQUIRED_PERMISSION_NOT_GRANTED) {
      store.update(s -> s.updateStep(SetupStep.PERMISSIONS_CHECK));
    } else {
      store.update(s -> s.updateStep(SetupStep.WIFI_DIRECT_UNAVAILABLE));
    }
  }

  public void checkPermissions() {
    Log.d(TAG, "Check for permissions");
    shutdown = false;
    store.update(s -> s.updateStep(SetupStep.PERMISSIONS_CHECK));
  }

  public void onPermissionsGranted() {
    Log.d(TAG, "Permissions granted");
    store.update(s -> s.updateStep(SetupStep.LOCATION_CHECK));
  }

  public void onLocationEnabled() {
    Log.d(TAG, "Location enabled");
    store.update(s -> s.updateStep(SetupStep.WIFI_CHECK));
  }

  public void onLocationDisabled() {
    Log.d(TAG, "Location disabled");
    store.update(s -> s.updateStep(SetupStep.LOCATION_DISABLED));
  }

  public void onWifiEnabled() {
    Log.d(TAG, "Wifi enabled");
    store.update(s -> s.updateStep(SetupStep.WIFI_DIRECT_CHECK));
  }

  public void onWifiDirectAvailable() {
    Log.d(TAG, "Wifi direct available");
    store.update(s -> s.updateStep(SetupStep.START));
  }

  public void onVerified() {
    store.update(s -> s.updateStep(SetupStep.WAITING_FOR_OTHER_TO_VERIFY));
  }

  public void onResume() {
    store.update(s -> {
      if (s.getCurrentSetupStep() == SetupStep.WIFI_DISABLED) {
        return s.updateStep(SetupStep.WIFI_CHECK);
      } else if (s.getCurrentSetupStep() == SetupStep.LOCATION_DISABLED) {
        return s.updateStep(SetupStep.LOCATION_CHECK);
      }
      return s;
    });
  }

  public void onWaitingTookTooLong() {
    shutdown = true;
    store.update(s -> s.updateStep(SetupStep.TROUBLESHOOTING));
  }
}
