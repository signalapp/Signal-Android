package org.thoughtcrime.securesms.devicetransfer.olddevice;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import org.signal.core.util.eventbus.EventBus;
import org.signal.core.util.eventbus.RestartableSubscription;
import org.signal.core.util.eventbus.ThreadMode;
import org.signal.devicetransfer.DeviceToDeviceTransferService;
import org.signal.devicetransfer.TransferStatus;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.devicetransfer.DeviceTransferFragment;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import java.text.NumberFormat;

/**
 * Shows transfer progress on the old device. Most logic is in {@link DeviceTransferFragment}
 * and it delegates to this class for strings, navigation, and updating progress.
 */
public final class OldDeviceTransferFragment extends DeviceTransferFragment {

  private final ClientTaskListener      clientTaskListener     = new ClientTaskListener();
  private final RestartableSubscription clientTaskSubscription = new RestartableSubscription(() -> EventBus.getDefault().subscribe(OldDeviceClientTask.Status.class, ThreadMode.MAIN, clientTaskListener::onStatus));

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    clientTaskSubscription.start();
  }

  @Override
  public void onDestroyView() {
    clientTaskSubscription.stop();
    super.onDestroyView();
  }

  @Override
  protected void navigateToRestartTransfer() {
    SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.action_directly_to_oldDeviceTransferInstructions);
  }

  @Override
  protected void navigateAwayFromTransfer() {
    clientTaskSubscription.stop();
    requireActivity().finish();
  }

  @Override
  protected void navigateToTransferComplete() {
    SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.action_oldDeviceTransfer_to_oldDeviceTransferComplete);
  }

  private class ClientTaskListener {
    private final NumberFormat formatter;

    public ClientTaskListener() {
      formatter = NumberFormat.getInstance();
      formatter.setMinimumFractionDigits(1);
      formatter.setMaximumFractionDigits(1);
    }

    public void onStatus(@NonNull OldDeviceClientTask.Status event) {
      if (event.isDone()) {
        setTransferFinished(true);
        ignoreTransferStatusEvents();
        EventBus.getDefault().removeStickyEvent(TransferStatus.class);
        DeviceToDeviceTransferService.stop(requireContext());
        SafeNavigation.safeNavigate(NavHostFragment.findNavController(OldDeviceTransferFragment.this), R.id.action_oldDeviceTransfer_to_oldDeviceTransferComplete);
      } else {
        if (event.getEstimatedMessageCount() == 0) {
          getStatus().setText(getString(R.string.DeviceTransfer__d_messages_so_far, event.getMessageCount()));
        } else {
          getStatus().setText(getString(R.string.DeviceTransfer__s_of_messages_so_far, formatter.format(event.getCompletionPercentage())));
        }
      }
    }
  }
}
