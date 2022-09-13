package org.thoughtcrime.securesms.payments.preferences.addmoney;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.qr.QrView;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;

public final class PaymentsAddMoneyFragment extends LoggingFragment {

  public PaymentsAddMoneyFragment() {
    super(R.layout.payments_add_money_fragment);
  }

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    PaymentsAddMoneyViewModel viewModel = new ViewModelProvider(this, new PaymentsAddMoneyViewModel.Factory()).get(PaymentsAddMoneyViewModel.class);

    Toolbar           toolbar                  = view.findViewById(R.id.payments_add_money_toolbar);
    QrView            qrImageView              = view.findViewById(R.id.payments_add_money_qr_image);
    TextView          walletAddressAbbreviated = view.findViewById(R.id.payments_add_money_abbreviated_wallet_address);
    View              copyAddress              = view.findViewById(R.id.payments_add_money_copy_address_button);
    LearnMoreTextView info                     = view.findViewById(R.id.payments_add_money_info);

    info.setLearnMoreVisible(true);
    info.setLink(getString(R.string.PaymentsAddMoneyFragment__learn_more__information));

    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    viewModel.getSelfAddressAbbreviated().observe(getViewLifecycleOwner(), walletAddressAbbreviated::setText);

    viewModel.getSelfAddressB58().observe(getViewLifecycleOwner(), base58 -> copyAddress.setOnClickListener(v -> copyAddressToClipboard(base58)));

    // Note we are choosing to put Base58 directly into QR here
    viewModel.getSelfAddressB58().observe(getViewLifecycleOwner(), qrImageView::setQrText);

    viewModel.getErrors().observe(getViewLifecycleOwner(), error -> {
      switch (error) {
        case PAYMENTS_NOT_ENABLED: throw new AssertionError("Payments are not enabled");
        default                  : throw new AssertionError();
      }
    });
  }

  private void copyAddressToClipboard(@NonNull String base58) {
    Context          context   = requireContext();
    ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), base58));

    Toast.makeText(context, R.string.PaymentsAddMoneyFragment__copied_to_clipboard, Toast.LENGTH_SHORT).show();
  }
}
