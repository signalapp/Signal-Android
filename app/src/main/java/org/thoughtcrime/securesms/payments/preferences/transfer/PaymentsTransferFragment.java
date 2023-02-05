package org.thoughtcrime.securesms.payments.preferences.transfer;

import android.Manifest;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.payments.MobileCoinPublicAddress;
import org.thoughtcrime.securesms.payments.preferences.model.PayeeParcelable;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

public final class PaymentsTransferFragment extends LoggingFragment {

  private static final String TAG = Log.tag(PaymentsTransferFragment.class);

  private EditText address;

  public PaymentsTransferFragment() {
    super(R.layout.payments_transfer_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    PaymentsTransferViewModel viewModel = new ViewModelProvider(Navigation.findNavController(view).getViewModelStoreOwner(R.id.payments_transfer), new PaymentsTransferViewModel.Factory()).get(PaymentsTransferViewModel.class);

    Toolbar toolbar = view.findViewById(R.id.payments_transfer_toolbar);

    view.findViewById(R.id.payments_transfer_scan_qr).setOnClickListener(v -> scanQrCode());
    view.findViewById(R.id.payments_transfer_next).setOnClickListener(v -> next(viewModel.getOwnAddress()));

    address = view.findViewById(R.id.payments_transfer_to_address);
    address.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        return next(viewModel.getOwnAddress());
      }
      return false;
    });

    viewModel.getAddress().observe(getViewLifecycleOwner(), address::setText);

    toolbar.setNavigationOnClickListener(v -> {
      ViewUtil.hideKeyboard(requireContext(), v);
      Navigation.findNavController(v).popBackStack();
    });
  }

  private boolean next(@NonNull MobileCoinPublicAddress ownAddress) {
    try {
      String                  base58Address = address.getText().toString();
      MobileCoinPublicAddress publicAddress = MobileCoinPublicAddress.fromBase58(base58Address);

      if (ownAddress.equals(publicAddress)) {
        new AlertDialog.Builder(requireContext())
                       .setTitle(R.string.PaymentsTransferFragment__invalid_address)
                       .setMessage(R.string.PaymentsTransferFragment__you_cant_transfer_to_your_own_signal_wallet_address)
                       .setPositiveButton(android.R.string.ok, null)
                       .show();
        return false;
      }

      NavDirections action = PaymentsTransferFragmentDirections.actionPaymentsTransferToCreatePayment(new PayeeParcelable(publicAddress))
                                                               .setFinishOnConfirm(PaymentsTransferFragmentArgs.fromBundle(requireArguments()).getFinishOnConfirm());

      SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), action);
      return true;
    } catch (MobileCoinPublicAddress.AddressException e) {
      Log.w(TAG, "Address is not valid", e);
      new AlertDialog.Builder(requireContext())
                     .setTitle(R.string.PaymentsTransferFragment__invalid_address)
                     .setMessage(R.string.PaymentsTransferFragment__check_the_wallet_address)
                     .setPositiveButton(android.R.string.ok, null)
                     .show();
      return false;
    }
  }

  private void scanQrCode() {
    Permissions.with(this)
               .request(Manifest.permission.CAMERA)
               .ifNecessary()
               .withRationaleDialog(getString(R.string.PaymentsTransferFragment__to_scan_a_qr_code_signal_needs), R.drawable.ic_camera_24)
               .onAnyPermanentlyDenied(this::onCameraPermissionPermanentlyDenied)
               .onAllGranted(() -> SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), R.id.action_paymentsTransfer_to_paymentsScanQr))
               .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.PaymentsTransferFragment__to_scan_a_qr_code_signal_needs_access_to_the_camera, Toast.LENGTH_LONG).show())
               .execute();
  }

  private void onCameraPermissionPermanentlyDenied() {
    new AlertDialog.Builder(requireContext())
                   .setTitle(R.string.Permissions_permission_required)
                   .setMessage(R.string.PaymentsTransferFragment__signal_needs_the_camera_permission_to_capture_qr_code_go_to_settings)
                   .setPositiveButton(R.string.PaymentsTransferFragment__settings, (dialog, which) -> requireActivity().startActivity(Permissions.getApplicationSettingsIntent(requireContext())))
                   .setNegativeButton(android.R.string.cancel, null)
                   .show();
  }

  @Override
  @SuppressWarnings("deprecation")
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }
}
