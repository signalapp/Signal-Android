package org.thoughtcrime.securesms.devicetransfer.olddevice;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;

/**
 * Shown after the old device successfully completes sending a backup to the new device.
 */
public final class OldDeviceTransferCompleteFragment extends LoggingFragment {
  public OldDeviceTransferCompleteFragment() {
    super(R.layout.old_device_transfer_complete_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    view.findViewById(R.id.old_device_transfer_complete_fragment_close)
        .setOnClickListener(v -> close());
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        close();
      }
    });
  }

  private void close() {
    OldDeviceExitActivity.exit(requireActivity());
  }
}
