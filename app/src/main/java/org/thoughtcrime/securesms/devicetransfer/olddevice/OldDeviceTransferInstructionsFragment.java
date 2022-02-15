package org.thoughtcrime.securesms.devicetransfer.olddevice;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import org.greenrobot.eventbus.EventBus;
import org.signal.devicetransfer.DeviceToDeviceTransferService;
import org.signal.devicetransfer.TransferStatus;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

/**
 * Provides instructions for the old device on how to start a device-to-device transfer.
 */
public final class OldDeviceTransferInstructionsFragment extends LoggingFragment {

  public OldDeviceTransferInstructionsFragment() {
    super(R.layout.old_device_transfer_instructions_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar toolbar = view.findViewById(R.id.old_device_transfer_instructions_fragment_toolbar);
    toolbar.setNavigationOnClickListener(v -> {
      if (!Navigation.findNavController(v).popBackStack()) {
        requireActivity().finish();
      }
    });

    view.findViewById(R.id.old_device_transfer_instructions_fragment_continue)
        .setOnClickListener(v -> SafeNavigation.safeNavigate(Navigation.findNavController(v), R.id.action_oldDeviceTransferInstructions_to_oldDeviceTransferSetup));
  }

  @Override
  public void onResume() {
    super.onResume();
    if (EventBus.getDefault().getStickyEvent(TransferStatus.class) != null) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this),
                                  R.id.action_oldDeviceTransferInstructions_to_oldDeviceTransferSetup);
    } else {
      DeviceToDeviceTransferService.stop(requireContext());
    }
  }
}
