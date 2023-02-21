package org.thoughtcrime.securesms.devicetransfer.olddevice;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor;

/**
 * Blocking dialog shown on old devices after a successful transfer to prevent use unless
 * the user takes action to reactivate.
 */
public final class OldDeviceTransferLockedDialog extends DialogFragment {

  private static final String TAG          = Log.tag(OldDeviceTransferLockedDialog.class);
  private static final String FRAGMENT_TAG = "OldDeviceTransferLockedDialog";

  public static void show(@NonNull FragmentManager fragmentManager) {
    if (fragmentManager.findFragmentByTag(FRAGMENT_TAG) != null) {
      Log.i(TAG, "Locked dialog already being shown");
      return;
    }

    new OldDeviceTransferLockedDialog().show(fragmentManager, FRAGMENT_TAG);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setCancelable(false);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(requireContext());
    dialogBuilder.setView(R.layout.old_device_transfer_locked_dialog_fragment)
                 .setPositiveButton(R.string.OldDeviceTransferLockedDialog__done, (d, w) -> OldDeviceExitActivity.exit(requireActivity()))
                 .setNegativeButton(R.string.OldDeviceTransferLockedDialog__cancel_and_activate_this_device, (d, w) -> onUnlockRequest());

    return dialogBuilder.create();
  }

  private void onUnlockRequest() {
    SignalStore.misc().clearOldDeviceTransferLocked();
    DeviceTransferBlockingInterceptor.getInstance().unblockNetwork();
  }
}
