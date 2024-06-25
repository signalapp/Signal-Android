package org.thoughtcrime.securesms.devicetransfer.newdevice;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.databinding.FragmentTransferRestoreBinding;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

/**
 * Simple jumping off menu to starts a device-to-device transfer or restore a backup.
 */
public final class TransferOrRestoreFragment extends LoggingFragment {

  private final LifecycleDisposable lifecycleDisposable = new LifecycleDisposable();

  private FragmentTransferRestoreBinding binding;

  public TransferOrRestoreFragment() {
    super(R.layout.fragment_transfer_restore);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    binding = FragmentTransferRestoreBinding.bind(view);

    TransferOrRestoreViewModel viewModel = new ViewModelProvider(this).get(TransferOrRestoreViewModel.class);

    binding.transferOrRestoreFragmentTransfer.setOnClickListener(v -> viewModel.onTransferFromAndroidDeviceSelected());
    binding.transferOrRestoreFragmentRestore.setOnClickListener(v -> viewModel.onRestoreFromLocalBackupSelected());
    binding.transferOrRestoreFragmentRestoreRemote.setOnClickListener(v -> viewModel.onRestoreFromRemoteBackupSelected());
    binding.transferOrRestoreFragmentNext.setOnClickListener(v -> launchSelection(viewModel.getStateSnapshot()));
    binding.transferOrRestoreFragmentMoreOptions.setOnClickListener(v -> SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), R.id.action_transferOrRestore_to_moreOptions));

    int visibility = RemoteConfig.messageBackups() ? View.VISIBLE : View.GONE;
    binding.transferOrRestoreFragmentRestoreRemoteCard.setVisibility(visibility);
    binding.transferOrRestoreFragmentMoreOptions.setVisibility(visibility);

    String description = getString(R.string.TransferOrRestoreFragment__transfer_your_account_and_messages_from_your_old_android_device);
    String toBold      = getString(R.string.TransferOrRestoreFragment__you_need_access_to_your_old_device);

    binding.transferOrRestoreFragmentTransferDescription.setText(SpanUtil.boldSubstring(description, toBold));

    lifecycleDisposable.bindTo(getViewLifecycleOwner());
    lifecycleDisposable.add(viewModel.getState().subscribe(this::updateSelection));
  }

  private void updateSelection(BackupRestorationType restorationType) {
    binding.transferOrRestoreFragmentTransferCard.setSelected(restorationType == BackupRestorationType.DEVICE_TRANSFER);
    binding.transferOrRestoreFragmentRestoreCard.setSelected(restorationType == BackupRestorationType.LOCAL_BACKUP);
    binding.transferOrRestoreFragmentRestoreRemoteCard.setSelected(restorationType == BackupRestorationType.REMOTE_BACKUP);
  }

  private void launchSelection(BackupRestorationType restorationType) {
    switch (restorationType) {
      case DEVICE_TRANSFER -> SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), R.id.action_new_device_transfer_instructions);
      case LOCAL_BACKUP -> SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), R.id.action_transfer_or_restore_to_local_restore);
      case REMOTE_BACKUP -> {}
      default -> throw new IllegalArgumentException();
    }
  }
}
