package org.thoughtcrime.securesms.payments.preferences;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.PaymentPreferencesDirections;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.help.HelpFragment;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.payments.FiatMoneyUtil;
import org.thoughtcrime.securesms.payments.MoneyView;
import org.thoughtcrime.securesms.payments.preferences.model.PaymentItem;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

public class PaymentsHomeFragment extends LoggingFragment {

  private static final String TAG = Log.tag(PaymentsHomeFragment.class);

  private PaymentsHomeViewModel viewModel;

  private final OnBackPressed onBackPressed = new OnBackPressed();

  public PaymentsHomeFragment() {
    super(R.layout.payments_home_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar             toolbar          = view.findViewById(R.id.payments_home_fragment_toolbar);
    RecyclerView        recycler         = view.findViewById(R.id.payments_home_fragment_recycler);
    View                header           = view.findViewById(R.id.payments_home_fragment_header);
    MoneyView           balance          = view.findViewById(R.id.payments_home_fragment_header_balance);
    TextView            exchange         = view.findViewById(R.id.payments_home_fragment_header_exchange);
    View                addMoney         = view.findViewById(R.id.button_start_frame);
    View                sendMoney        = view.findViewById(R.id.button_end_frame);
    View                refresh          = view.findViewById(R.id.payments_home_fragment_header_refresh);
    LottieAnimationView refreshAnimation = view.findViewById(R.id.payments_home_fragment_header_refresh_animation);

    toolbar.setNavigationOnClickListener(v -> {
      viewModel.markAllPaymentsSeen();
      requireActivity().finish();
    });

    toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);

    addMoney.setOnClickListener(v -> {
      if (SignalStore.paymentsValues().getPaymentsAvailability().isSendAllowed()) {
        SafeNavigation.safeNavigate(Navigation.findNavController(v), PaymentsHomeFragmentDirections.actionPaymentsHomeToPaymentsAddMoney());
      } else {
        showPaymentsDisabledDialog();
      }
    });
    sendMoney.setOnClickListener(v -> {
      if (SignalStore.paymentsValues().getPaymentsAvailability().isSendAllowed()) {
        SafeNavigation.safeNavigate(Navigation.findNavController(v), PaymentsHomeFragmentDirections.actionPaymentsHomeToPaymentRecipientSelectionFragment());
      } else {
        showPaymentsDisabledDialog();
      }
    });

    PaymentsHomeAdapter adapter = new PaymentsHomeAdapter(new HomeCallbacks());
    recycler.setAdapter(adapter);

    viewModel = ViewModelProviders.of(this, new PaymentsHomeViewModel.Factory()).get(PaymentsHomeViewModel.class);

    viewModel.getList().observe(getViewLifecycleOwner(), list -> {
      boolean hadPaymentItems = Stream.of(adapter.getCurrentList()).anyMatch(model -> model instanceof PaymentItem);

      if (!hadPaymentItems) {
        adapter.submitList(list, () -> recycler.scrollToPosition(0));
      } else {
        adapter.submitList(list);
      }
    });

    viewModel.getPaymentsEnabled().observe(getViewLifecycleOwner(), enabled -> {
      if (enabled) {
        toolbar.inflateMenu(R.menu.payments_home_fragment_menu);
      } else {
        toolbar.getMenu().clear();
      }
      header.setVisibility(enabled ? View.VISIBLE : View.GONE);
    });
    viewModel.getBalance().observe(getViewLifecycleOwner(), balance::setMoney);
    viewModel.getExchange().observe(getViewLifecycleOwner(), amount -> {
      if (amount != null) {
        exchange.setText(FiatMoneyUtil.format(getResources(), amount));
      } else {
        exchange.setText(R.string.PaymentsHomeFragment__unknown_amount);
      }
    });

    refresh.setOnClickListener(v -> viewModel.refreshExchangeRates(true));
    exchange.setOnClickListener(v -> viewModel.refreshExchangeRates(true));

    viewModel.getExchangeLoadState().observe(getViewLifecycleOwner(), loadState -> {
      switch (loadState) {
        case INITIAL:
        case LOADED:
          refresh.setVisibility(View.VISIBLE);
          refreshAnimation.cancelAnimation();
          refreshAnimation.setVisibility(View.GONE);
          break;
        case LOADING:
          refresh.setVisibility(View.INVISIBLE);
          refreshAnimation.playAnimation();
          refreshAnimation.setVisibility(View.VISIBLE);
          break;
        case ERROR:
          refresh.setVisibility(View.VISIBLE);
          refreshAnimation.cancelAnimation();
          refreshAnimation.setVisibility(View.GONE);
          exchange.setText(R.string.PaymentsHomeFragment__currency_conversion_not_available);
          Toast.makeText(view.getContext(), R.string.PaymentsHomeFragment__cant_display_currency_conversion, Toast.LENGTH_SHORT).show();
          break;
      }
    });

    viewModel.getPaymentStateEvents().observe(getViewLifecycleOwner(), paymentStateEvent -> {
      AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

      builder.setTitle(R.string.PaymentsHomeFragment__deactivate_payments_question);
      builder.setMessage(R.string.PaymentsHomeFragment__you_will_not_be_able_to_send);
      builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());

      switch (paymentStateEvent) {
        case NO_BALANCE:
          Toast.makeText(requireContext(), R.string.PaymentsHomeFragment__balance_is_not_currently_available, Toast.LENGTH_SHORT).show();
          return;
        case DEACTIVATED:
          Snackbar.make(requireView(), R.string.PaymentsHomeFragment__payments_deactivated, Snackbar.LENGTH_SHORT)
                  .setTextColor(Color.WHITE)
                  .show();
          return;
        case DEACTIVATE_WITHOUT_BALANCE:
          builder.setPositiveButton(SpanUtil.color(ContextCompat.getColor(requireContext(), R.color.signal_alert_primary),
                                                                          getString(R.string.PaymentsHomeFragment__deactivate)),
                                    (dialog, which) -> {
                                      viewModel.confirmDeactivatePayments();
                                      dialog.dismiss();
                                    });
          break;
        case DEACTIVATE_WITH_BALANCE:
          builder.setPositiveButton(getString(R.string.PaymentsHomeFragment__continue), (dialog, which) -> {
            dialog.dismiss();
            SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.deactivateWallet);
          });
          break;
        case ACTIVATED:
          return;
        default:
          throw new IllegalStateException("Unsupported event type: " + paymentStateEvent.name());
      }

      builder.show();
    });

    viewModel.getErrorEnablingPayments().observe(getViewLifecycleOwner(), errorEnabling -> {
      switch (errorEnabling) {
        case REGION:
          Toast.makeText(view.getContext(), R.string.PaymentsHomeFragment__payments_is_not_available_in_your_region, Toast.LENGTH_LONG).show();
          break;
        case NETWORK:
          Toast.makeText(view.getContext(), R.string.PaymentsHomeFragment__could_not_enable_payments, Toast.LENGTH_SHORT).show();
          break;
        default:
          throw new AssertionError();
      }
    });

    requireActivity().getOnBackPressedDispatcher().addCallback(onBackPressed);
  }

  @Override
  public void onResume() {
    super.onResume();
    viewModel.checkPaymentActivationState();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    onBackPressed.setEnabled(false);
  }

  private boolean onMenuItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.payments_home_fragment_menu_transfer_to_exchange) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.action_paymentsHome_to_paymentsTransfer);
      return true;
    } else if (item.getItemId() == R.id.payments_home_fragment_menu_set_currency) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.action_paymentsHome_to_setCurrency);
      return true;
    } else if (item.getItemId() == R.id.payments_home_fragment_menu_deactivate_wallet) {
      viewModel.deactivatePayments();
      return true;
    } else if (item.getItemId() == R.id.payments_home_fragment_menu_view_recovery_phrase) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.action_paymentsHome_to_paymentsBackup);
      return true;
    } else if (item.getItemId() == R.id.payments_home_fragment_menu_help) {
      startActivity(AppSettingsActivity.help(requireContext(), HelpFragment.PAYMENT_INDEX));
      return true;
    }

    return false;
  }

  private void showPaymentsDisabledDialog() {
    new AlertDialog.Builder(requireActivity())
                   .setMessage(R.string.PaymentsHomeFragment__payments_not_available)
                   .setPositiveButton(android.R.string.ok, null)
                   .show();
  }

  class HomeCallbacks implements PaymentsHomeAdapter.Callbacks {
    @Override
    public void onActivatePayments() {
      new MaterialAlertDialogBuilder(requireContext())
                     .setMessage(R.string.PaymentsHomeFragment__you_can_use_signal_to_send)
                     .setPositiveButton(R.string.PaymentsHomeFragment__activate, (dialog, which) -> {
                       viewModel.activatePayments();
                       dialog.dismiss();
                     })
                     .setNegativeButton(R.string.PaymentsHomeFragment__view_mobile_coin_terms, (dialog, which) -> {
                       CommunicationActions.openBrowserLink(requireContext(), getString(R.string.PaymentsHomeFragment__mobile_coin_terms_url));
                     })
                     .setNeutralButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                     .show();
    }

    @Override
    public void onRestorePaymentsAccount() {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(PaymentsHomeFragment.this),
                                  PaymentsHomeFragmentDirections.actionPaymentsHomeToPaymentsBackup().setIsRestore(true));
    }

    @Override
    public void onSeeAll(@NonNull PaymentType paymentType) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(PaymentsHomeFragment.this),
                                  PaymentsHomeFragmentDirections.actionPaymentsHomeToPaymentsAllActivity(paymentType));
    }

    @Override
    public void onPaymentItem(@NonNull PaymentItem model) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(PaymentsHomeFragment.this),
                                  PaymentPreferencesDirections.actionDirectlyToPaymentDetails(model.getPaymentDetailsParcelable()));
    }

    @Override
    public void onInfoCardDismissed() {
      viewModel.onInfoCardDismissed();
    }

    @Override
    public void onUpdatePin() {
      startActivityForResult(CreateKbsPinActivity.getIntentForPinChangeFromSettings(requireContext()), CreateKbsPinActivity.REQUEST_NEW_PIN);
    }

    @Override
    public void onViewRecoveryPhrase() {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(PaymentsHomeFragment.this), R.id.action_paymentsHome_to_paymentsBackup);
    }
  }

  private class OnBackPressed extends OnBackPressedCallback {

    public OnBackPressed() {
      super(true);
    }

    @Override
    public void handleOnBackPressed() {
      viewModel.markAllPaymentsSeen();
      requireActivity().finish();
    }
  }
}
