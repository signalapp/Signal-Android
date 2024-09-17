package org.thoughtcrime.securesms.payments.preferences;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.FlowLiveDataConversions;
import androidx.lifecycle.ViewModelProvider;
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
import org.thoughtcrime.securesms.banner.Banner;
import org.thoughtcrime.securesms.banner.BannerManager;
import org.thoughtcrime.securesms.banner.banners.EnclaveFailureBanner;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.help.HelpFragment;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity;
import org.thoughtcrime.securesms.payments.FiatMoneyUtil;
import org.thoughtcrime.securesms.payments.MoneyView;
import org.thoughtcrime.securesms.payments.backup.RecoveryPhraseStates;
import org.thoughtcrime.securesms.payments.backup.confirm.PaymentsRecoveryPhraseConfirmFragment;
import org.thoughtcrime.securesms.payments.preferences.model.InfoCard;
import org.thoughtcrime.securesms.payments.preferences.model.PaymentItem;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.PlayStoreUtil;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.views.Stub;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import kotlinx.coroutines.flow.Flow;

public class PaymentsHomeFragment extends LoggingFragment {
  private static final int DAYS_UNTIL_REPROMPT_PAYMENT_LOCK = 30;
  private static final int MAX_PAYMENT_LOCK_SKIP_COUNT      = 2;

  private static final String TAG = Log.tag(PaymentsHomeFragment.class);

  private PaymentsHomeViewModel viewModel;

  public PaymentsHomeFragment() {
    super(R.layout.payments_home_fragment);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    long    paymentLockTimestamp = SignalStore.payments().getPaymentLockTimestamp();
    boolean enablePaymentLock    = PaymentsHomeFragmentArgs.fromBundle(getArguments()).getEnablePaymentLock();
    boolean showPaymentLock      = SignalStore.payments().getPaymentLockSkipCount() < MAX_PAYMENT_LOCK_SKIP_COUNT &&
                                   (System.currentTimeMillis() >= paymentLockTimestamp);

    if (enablePaymentLock && showPaymentLock) {
      long waitUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(DAYS_UNTIL_REPROMPT_PAYMENT_LOCK);

      SignalStore.payments().setPaymentLockTimestamp(waitUntil);
      new MaterialAlertDialogBuilder(requireContext())
          .setTitle(getString(R.string.PaymentsHomeFragment__turn_on))
          .setMessage(getString(R.string.PaymentsHomeFragment__add_an_additional_layer))
          .setPositiveButton(R.string.PaymentsHomeFragment__enable, (dialog, which) ->
              SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), PaymentsHomeFragmentDirections.actionPaymentsHomeToPrivacySettings(true)))
          .setNegativeButton(R.string.PaymentsHomeFragment__not_now, (dialog, which) -> setSkipCount())
          .setCancelable(false)
          .show();
    }
  }

  private void setSkipCount() {
      int skipCount = SignalStore.payments().getPaymentLockSkipCount();
      SignalStore.payments().setPaymentLockSkipCount(++skipCount);
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
    Stub<ComposeView>   bannerView       = ViewUtil.findStubById(view, R.id.banner_compose_view);

    toolbar.setNavigationOnClickListener(v -> {
      viewModel.markAllPaymentsSeen();
      requireActivity().finish();
    });

    toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);

    addMoney.setOnClickListener(v -> {
      if (viewModel.isEnclaveFailurePresent()) {
        showUpdateIsRequiredDialog();
      } else if (SignalStore.payments().getPaymentsAvailability().isSendAllowed()) {
        SafeNavigation.safeNavigate(Navigation.findNavController(v), PaymentsHomeFragmentDirections.actionPaymentsHomeToPaymentsAddMoney());
      } else {
        showPaymentsDisabledDialog();
      }
    });
    sendMoney.setOnClickListener(v -> {
      if (viewModel.isEnclaveFailurePresent()) {
        showUpdateIsRequiredDialog();
      } else if (SignalStore.payments().getPaymentsAvailability().isSendAllowed()) {
        SafeNavigation.safeNavigate(Navigation.findNavController(v), PaymentsHomeFragmentDirections.actionPaymentsHomeToPaymentRecipientSelectionFragment());
      } else {
        showPaymentsDisabledDialog();
      }
    });

    PaymentsHomeAdapter adapter = new PaymentsHomeAdapter(new HomeCallbacks());
    recycler.setAdapter(adapter);

    viewModel = new ViewModelProvider(this, new PaymentsHomeViewModel.Factory()).get(PaymentsHomeViewModel.class);

    getParentFragmentManager().setFragmentResultListener(PaymentsRecoveryPhraseConfirmFragment.REQUEST_KEY_RECOVERY_PHRASE, this, (requestKey, result) -> {
      if (result.getBoolean(PaymentsRecoveryPhraseConfirmFragment.RECOVERY_PHRASE_CONFIRMED)) {
        viewModel.updateStore();
      }
    });

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

    viewModel.getBalance().observe(getViewLifecycleOwner(), balanceAmount -> {
      balance.setMoney(balanceAmount);
      if (SignalStore.payments().getShowSaveRecoveryPhrase() &&
          !SignalStore.payments().getUserConfirmedMnemonic() &&
          !balanceAmount.isEqualOrLessThanZero()) {
        SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), PaymentsHomeFragmentDirections.actionPaymentsHomeToPaymentsBackup().setRecoveryPhraseState(RecoveryPhraseStates.FIRST_TIME_NON_ZERO_BALANCE_WITH_MNEMONIC_NOT_CONFIRMED));
        SignalStore.payments().setShowSaveRecoveryPhrase(false);
      }
    });

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
      MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

      builder.setTitle(R.string.PaymentsHomeFragment__deactivate_payments_question);
      builder.setMessage(R.string.PaymentsHomeFragment__you_will_not_be_able_to_send);
      builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());

      switch (paymentStateEvent) {
        case NO_BALANCE:
          Toast.makeText(requireContext(), R.string.PaymentsHomeFragment__balance_is_not_currently_available, Toast.LENGTH_SHORT).show();
          return;
        case DEACTIVATED:
          Snackbar.make(requireView(), R.string.PaymentsHomeFragment__payments_deactivated, Snackbar.LENGTH_SHORT)
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
            SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.action_paymentsHome_to_deactivateWallet);
          });
          break;
        case ACTIVATED:
          if (!SignalStore.payments().isPaymentLockEnabled()) {
            SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.action_paymentsHome_to_securitySetup);
          }
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

    viewModel.getEnclaveFailure().observe(getViewLifecycleOwner(), failure -> {
      if (failure) {
        showUpdateIsRequiredDialog();
      }

      BannerManager bannerManager = new BannerManager(List.of(new EnclaveFailureBanner(failure)));
      bannerManager.updateContent(bannerView.get());
    });

    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressed());
  }

  @Override
  public void onResume() {
    super.onResume();
    viewModel.checkPaymentActivationState();
  }

  private void showUpdateIsRequiredDialog() {
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.PaymentsHomeFragment__update_required))
        .setMessage(getString(R.string.PaymentsHomeFragment__an_update_is_required))
        .setPositiveButton(R.string.PaymentsHomeFragment__update_now, (dialog, which) -> { PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext()); })
        .setNegativeButton(R.string.PaymentsHomeFragment__cancel, (dialog, which) -> {})
        .setCancelable(false)
        .show();
  }

  private boolean onMenuItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.payments_home_fragment_menu_transfer_to_exchange) {
      if (viewModel.isEnclaveFailurePresent()) {
        showUpdateIsRequiredDialog();
      } else {
        SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.action_paymentsHome_to_paymentsTransfer);
      }
      return true;
    } else if (item.getItemId() == R.id.payments_home_fragment_menu_set_currency) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.action_paymentsHome_to_setCurrency);
      return true;
    } else if (item.getItemId() == R.id.payments_home_fragment_menu_deactivate_wallet) {
      viewModel.deactivatePayments();
      return true;
    } else if (item.getItemId() == R.id.payments_home_fragment_menu_view_recovery_phrase) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this),
                                  PaymentsHomeFragmentDirections.actionPaymentsHomeToPaymentsBackup().setRecoveryPhraseState(SignalStore.payments().isMnemonicConfirmed() ?
                                                                                        RecoveryPhraseStates.FROM_PAYMENTS_MENU_WITH_MNEMONIC_CONFIRMED :
                                                                                        RecoveryPhraseStates.FROM_PAYMENTS_MENU_WITH_MNEMONIC_NOT_CONFIRMED));
      return true;
    } else if (item.getItemId() == R.id.payments_home_fragment_menu_help) {
      startActivity(AppSettingsActivity.help(requireContext(), HelpFragment.PAYMENT_INDEX));
      return true;
    }

    return false;
  }

  private void showPaymentsDisabledDialog() {
    new MaterialAlertDialogBuilder(requireActivity())
                   .setMessage(R.string.PaymentsHomeFragment__payments_not_available)
                   .setPositiveButton(android.R.string.ok, null)
                   .show();
  }

  class HomeCallbacks implements PaymentsHomeAdapter.Callbacks {
    @Override
    public void onActivatePayments() {
      new MaterialAlertDialogBuilder(requireContext())
                     .setMessage(R.string.PaymentsHomeFragment__you_can_use_signal_to_send_and)
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
    public void onInfoCardDismissed(InfoCard.Type type) {
      viewModel.updateStore();
      if (type == InfoCard.Type.RECORD_RECOVERY_PHASE) {
        showSaveRecoveryPhrase();
      }
    }

    @Override
    public void onUpdatePin() {
      startActivityForResult(CreateSvrPinActivity.getIntentForPinChangeFromSettings(requireContext()), CreateSvrPinActivity.REQUEST_NEW_PIN);
    }

    @Override
    public void onViewRecoveryPhrase() {
      showSaveRecoveryPhrase();
    }

    private void showSaveRecoveryPhrase() {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(PaymentsHomeFragment.this),
                                  PaymentsHomeFragmentDirections.actionPaymentsHomeToPaymentsBackup().setRecoveryPhraseState(RecoveryPhraseStates.FROM_INFO_CARD_WITH_MNEMONIC_NOT_CONFIRMED));
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
