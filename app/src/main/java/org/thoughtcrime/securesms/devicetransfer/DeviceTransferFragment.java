package org.thoughtcrime.securesms.devicetransfer;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.devicetransfer.DeviceToDeviceTransferService;
import org.signal.devicetransfer.TransferStatus;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;

/**
 * Drives the UI for the actual device transfer progress. Shown after setup is complete
 * and the two devices are transferring.
 * <p>
 * Handles show progress and error state.
 */
public abstract class DeviceTransferFragment extends LoggingFragment {

  private static final String TRANSFER_FINISHED_KEY = "transfer_finished";

  private final OnBackPressed        onBackPressed        = new OnBackPressed();
  private final TransferModeListener transferModeListener = new TransferModeListener();

  protected TextView title;
  protected View     tryAgain;
  protected Button   cancel;
  protected View     progress;
  protected View     alert;
  protected TextView status;
  protected boolean  transferFinished;

  public DeviceTransferFragment() {
    super(R.layout.fragment_device_transfer);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (savedInstanceState != null) {
      transferFinished = savedInstanceState.getBoolean(TRANSFER_FINISHED_KEY);
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    if (transferFinished) {
      navigateToTransferComplete();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(TRANSFER_FINISHED_KEY, transferFinished);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    title    = view.findViewById(R.id.device_transfer_fragment_title);
    tryAgain = view.findViewById(R.id.device_transfer_fragment_try_again);
    cancel   = view.findViewById(R.id.device_transfer_fragment_cancel);
    progress = view.findViewById(R.id.device_transfer_fragment_progress);
    alert    = view.findViewById(R.id.device_transfer_fragment_alert);
    status   = view.findViewById(R.id.device_transfer_fragment_status);

    cancel.setOnClickListener(v -> cancelActiveTransfer());
    tryAgain.setOnClickListener(v -> {
      EventBus.getDefault().unregister(transferModeListener);
      EventBus.getDefault().removeStickyEvent(TransferStatus.class);
      navigateToRestartTransfer();
    });

    EventBus.getDefault().register(transferModeListener);
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressed);
  }

  @Override
  public void onDestroyView() {
    EventBus.getDefault().unregister(transferModeListener);
    super.onDestroyView();
  }

  private void cancelActiveTransfer() {
    new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.DeviceTransfer__stop_transfer)
                                                    .setMessage(R.string.DeviceTransfer__all_transfer_progress_will_be_lost)
                                                    .setPositiveButton(R.string.DeviceTransfer__stop_transfer, (d, w) -> {
                                                      EventBus.getDefault().unregister(transferModeListener);
                                                      DeviceToDeviceTransferService.stop(requireContext());
                                                      EventBus.getDefault().removeStickyEvent(TransferStatus.class);
                                                      navigateAwayFromTransfer();
                                                    })
                                                    .setNegativeButton(android.R.string.cancel, null)
                                                    .show();
  }

  protected void ignoreTransferStatusEvents() {
    EventBus.getDefault().unregister(transferModeListener);
  }

  protected abstract void navigateToRestartTransfer();

  protected abstract void navigateAwayFromTransfer();

  protected abstract void navigateToTransferComplete();

  private class TransferModeListener {
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(@NonNull TransferStatus event) {
      if (event.getTransferMode() != TransferStatus.TransferMode.SERVICE_CONNECTED) {
        abort();
      }
    }
  }

  protected void abort() {
    abort(R.string.DeviceTransfer__transfer_failed);
  }

  protected void abort(@StringRes int errorMessage) {
    EventBus.getDefault().unregister(transferModeListener);
    DeviceToDeviceTransferService.stop(requireContext());

    progress.setVisibility(View.GONE);
    alert.setVisibility(View.VISIBLE);
    tryAgain.setVisibility(View.VISIBLE);

    title.setText(R.string.DeviceTransfer__unable_to_transfer);
    status.setText(errorMessage);
    cancel.setText(R.string.DeviceTransfer__cancel);
    cancel.setOnClickListener(v -> navigateAwayFromTransfer());

    onBackPressed.isActiveTransfer = false;
  }

  protected class OnBackPressed extends OnBackPressedCallback {

    private boolean isActiveTransfer = true;

    public OnBackPressed() {
      super(true);
    }

    @Override
    public void handleOnBackPressed() {
      if (isActiveTransfer) {
        cancelActiveTransfer();
      } else {
        navigateAwayFromTransfer();
      }
    }
  }
}
