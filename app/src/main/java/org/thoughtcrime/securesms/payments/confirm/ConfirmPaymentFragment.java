package org.thoughtcrime.securesms.payments.confirm;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BiometricDeviceAuthentication;
import org.thoughtcrime.securesms.BiometricDeviceLockContract;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.CanNotSendPaymentDialog;
import org.thoughtcrime.securesms.payments.FiatMoneyUtil;
import org.thoughtcrime.securesms.payments.Payee;
import org.thoughtcrime.securesms.payments.preferences.PaymentsHomeFragmentDirections;
import org.thoughtcrime.securesms.payments.preferences.RecipientHasNotEnabledPaymentsDialog;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.signal.core.util.StringUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.whispersystems.signalservice.api.payments.FormatterOptions;

import java.util.concurrent.TimeUnit;

import kotlin.Unit;

public class ConfirmPaymentFragment extends BottomSheetDialogFragment {
  private static final String                         TAG           = Log.tag(ConfirmPaymentFragment.class);
  private              ConfirmPaymentViewModel        viewModel;
  private              ActivityResultLauncher<String> activityResultLauncher;
  private              BiometricDeviceAuthentication  biometricAuth;
  private final        Runnable                       dismiss       = () ->
  {
    dismissAllowingStateLoss();

    if (ConfirmPaymentFragmentArgs.fromBundle(requireArguments()).getFinishOnConfirm()) {
      requireActivity().setResult(Activity.RESULT_OK);
      requireActivity().finish();
    } else {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), PaymentsHomeFragmentDirections.actionDirectlyToPaymentsHome(!isPaymentLockEnabled(requireContext())));
    }
  };

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL, R.style.Signal_DayNight_BottomSheet_Rounded);
    super.onCreate(savedInstanceState);
  }

  @Override
  public @NonNull Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
    dialog.getBehavior().setHideable(false);
    return dialog;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.confirm_payment_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    ConfirmPaymentViewModel.Factory factory = new ConfirmPaymentViewModel.Factory(ConfirmPaymentFragmentArgs.fromBundle(requireArguments()).getCreatePaymentDetails());
    viewModel = new ViewModelProvider(this, factory).get(ConfirmPaymentViewModel.class);

    RecyclerView          list    = view.findViewById(R.id.confirm_payment_fragment_list);
    ConfirmPaymentAdapter adapter = new ConfirmPaymentAdapter(new Callbacks());
    list.setAdapter(adapter);

    activityResultLauncher = registerForActivityResult(new BiometricDeviceLockContract(), result -> {
      if (result == BiometricDeviceAuthentication.AUTHENTICATED) {
        viewModel.confirmPayment();
      }
    });

    viewModel.getState().observe(getViewLifecycleOwner(), state -> adapter.submitList(createList(state)));
    viewModel.isPaymentDone().observe(getViewLifecycleOwner(), isDone -> {
      if (isDone) {
        ThreadUtil.runOnMainDelayed(dismiss, TimeUnit.SECONDS.toMillis(2));
      }
    });

    viewModel.getErrorTypeEvents().observe(getViewLifecycleOwner(), error -> {
      switch (error) {
        case NO_PROFILE_KEY:
          CanNotSendPaymentDialog.show(requireContext());
          break;
        case NO_ADDRESS:
          RecipientHasNotEnabledPaymentsDialog.show(requireContext());
          break;
        case CAN_NOT_GET_FEE:
          new MaterialAlertDialogBuilder(requireContext())
                         .setMessage(R.string.ConfirmPaymentFragment__unable_to_request_a_network_fee)
                         .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                           dialog.dismiss();
                           viewModel.refreshFee();
                         })
                         .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                           dialog.dismiss();
                           dismiss();
                         })
                         .setCancelable(false)
                         .show();
          break;
      }
    });

    BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo
                                                               .Builder()
                                                               .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
                                                               .setTitle(requireContext().getString(R.string.BiometricDeviceAuthentication__signal))
                                                               .setConfirmationRequired(false)
                                                               .build();
    biometricAuth = new BiometricDeviceAuthentication(BiometricManager.from(requireActivity()),
                                                      new BiometricPrompt(requireActivity(), new BiometricAuthenticationListener()),
                                                      promptInfo);
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);
    ThreadUtil.cancelRunnableOnMain(dismiss);
  }

  @Override
  public void onResume() {
    super.onResume();
    WindowUtil.initializeScreenshotSecurity(requireContext(), requireDialog().getWindow());
  }

  @Override
  public void onPause() {
    super.onPause();
    biometricAuth.cancelAuthentication();
  }

  private @NonNull MappingModelList createList(@NonNull ConfirmPaymentState state) {
    MappingModelList list      = new MappingModelList();
    FormatterOptions options   = FormatterOptions.defaults();

    switch (state.getFeeStatus()) {
      case STILL_LOADING:
      case ERROR:
        list.add(new ConfirmPaymentAdapter.LoadingItem());
        break;
      case NOT_SET:
      case SET:
        list.add(new ConfirmPaymentAdapter.LineItem(getToPayeeDescription(requireContext(), state), state.getAmount().toString(options)));
        if (state.getExchange() != null) {
          list.add(new ConfirmPaymentAdapter.LineItem(getString(R.string.ConfirmPayment__estimated_s, state.getExchange().getCurrency().getCurrencyCode()),
                                                      FiatMoneyUtil.format(getResources(), state.getExchange(), FiatMoneyUtil.formatOptions().withDisplayTime(false))));
        }
        list.add(new ConfirmPaymentAdapter.LineItem(getString(R.string.ConfirmPayment__network_fee), state.getFee().toString(options)));
        list.add(new ConfirmPaymentAdapter.Divider());
        list.add(new ConfirmPaymentAdapter.TotalLineItem(getString(R.string.ConfirmPayment__total_amount), state.getTotal().toString(options)));
    }

    list.add(new ConfirmPaymentAdapter.ConfirmPaymentStatus(state.getStatus(), state.getFeeStatus(), state.getBalance()));
    return list;
  }

  private static CharSequence getToPayeeDescription(Context context, @NonNull ConfirmPaymentState state) {
    return new SpannableStringBuilder().append(context.getString(R.string.ConfirmPayment__to))
                                       .append(' ')
                                       .append(getPayeeDescription(context, state.getPayee()));
  }

  private static CharSequence getPayeeDescription(Context context, @NonNull Payee payee) {
    return payee.hasRecipientId() ? Recipient.resolved(payee.requireRecipientId()).getDisplayName(context)
                                  : mono(context, StringUtil.abbreviateInMiddle(payee.requirePublicAddress().getPaymentAddressBase58(), 17));
  }

  private static CharSequence mono(Context context, CharSequence address) {
    SpannableString spannable = new SpannableString(address);
    spannable.setSpan(new TextAppearanceSpan(context, R.style.TextAppearance_Signal_Mono),
                      0,
                      address.length(),
                      Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }



  private boolean isPaymentLockEnabled(Context context) {
    return SignalStore.payments().isPaymentLockEnabled() && ServiceUtil.getKeyguardManager(context).isKeyguardSecure();
  }

  private class Callbacks implements ConfirmPaymentAdapter.Callbacks {

    @Override
    public void onConfirmPayment() {
      setCancelable(false);
      if (isPaymentLockEnabled(requireContext())) {
        boolean success = biometricAuth.authenticate(requireContext(), true, this::showConfirmDeviceCredentialIntent);
        if (!success) {
          setCancelable(true);
          new MaterialAlertDialogBuilder(requireContext())
              .setTitle(R.string.ConfirmPaymentFragment__failed_to_show_payment_lock)
              .setMessage(R.string.ConfirmPaymentFragment__you_enabled_payment_lock_in_the_settings)
              .setNeutralButton(android.R.string.ok, (d, i) -> d.dismiss())
              .setNegativeButton(R.string.ConfirmPaymentFragment__go_to_settings, (d, i) -> {
                startActivity(AppSettingsActivity.privacy(requireContext()));
                d.dismiss();
              })
              .show();
        }
      } else {
        viewModel.confirmPayment();
      }
    }

    public Unit showConfirmDeviceCredentialIntent() {
      activityResultLauncher.launch(getString(R.string.BiometricDeviceAuthentication__signal));
      return Unit.INSTANCE;
    }
  }

  private class BiometricAuthenticationListener extends BiometricPrompt.AuthenticationCallback {
    @Override
    public void onAuthenticationError(int errorCode, @NonNull CharSequence errorString) {
      Log.w(TAG, "Authentication error: " + errorCode);
      switch (errorCode) {
        case BiometricPrompt.ERROR_CANCELED:
        case BiometricPrompt.ERROR_USER_CANCELED:
          setCancelable(true);
          break;
        default:
          onAuthenticationFailed();
          break;
      }
    }

    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
      Log.i(TAG, "onAuthenticationSucceeded");
      viewModel.confirmPayment();
    }

    @Override
    public void onAuthenticationFailed() {
      Log.w(TAG, "Unable to authenticate payment lock");
    }
  }
}
