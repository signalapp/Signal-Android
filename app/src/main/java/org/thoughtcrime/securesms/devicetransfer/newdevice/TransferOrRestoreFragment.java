package org.thoughtcrime.securesms.devicetransfer.newdevice;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.SpanUtil;

/**
 * Simple jumping off menu to starts a device-to-device transfer or restore a backup.
 */
public final class TransferOrRestoreFragment extends LoggingFragment {

  public TransferOrRestoreFragment() {
    super(R.layout.fragment_transfer_restore);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    view.findViewById(R.id.transfer_or_restore_fragment_transfer)
        .setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.action_new_device_transfer_instructions));

    view.findViewById(R.id.transfer_or_restore_fragment_restore)
        .setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.action_choose_backup));

    String description = getString(R.string.TransferOrRestoreFragment__transfer_your_account_and_message_history_from_your_old_android_device);
    String toBold      = getString(R.string.TransferOrRestoreFragment__you_must_have_access_to_your_old_device);

    TextView transferDescriptionView = view.findViewById(R.id.transfer_or_restore_fragment_transfer_description);
    transferDescriptionView.setText(SpanUtil.boldSubstring(description, toBold));
  }
}
