package org.thoughtcrime.securesms.payments.preferences;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.PaymentPreferencesDirections;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.payments.preferences.model.PaymentItem;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

public class PaymentsPagerItemFragment extends LoggingFragment {

  private static final String PAYMENT_CATEGORY = "payment_category";

  private PaymentsPagerItemViewModel viewModel;

  static @NonNull Fragment getFragmentForAllPayments() {
    return getFragment(PaymentCategory.ALL);
  }

  static @NonNull Fragment getFragmentForSentPayments() {
    return getFragment(PaymentCategory.SENT);
  }

  static @NonNull Fragment getFragmentForReceivedPayments() {
    return getFragment(PaymentCategory.RECEIVED);
  }

  private static @NonNull Fragment getFragment(@NonNull PaymentCategory paymentCategory) {
    Bundle arguments = new Bundle();
    arguments.putString(PAYMENT_CATEGORY, paymentCategory.getCode());

    Fragment fragment = new PaymentsPagerItemFragment();
    fragment.setArguments(arguments);
    return fragment;
  }

  public PaymentsPagerItemFragment() {
    super(R.layout.payment_preferences_all_pager_item_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    PaymentsPagerItemViewModel.Factory factory = new PaymentsPagerItemViewModel.Factory(PaymentCategory.forCode(requireArguments().getString(PAYMENT_CATEGORY)));
    viewModel = ViewModelProviders.of(this, factory).get(PaymentsPagerItemViewModel.class);

    RecyclerView        recycler = view.findViewById(R.id.payments_activity_pager_item_fragment_recycler);
    PaymentsHomeAdapter adapter  = new PaymentsHomeAdapter(new Callbacks());

    recycler.setAdapter(adapter);

    viewModel.getList().observe(getViewLifecycleOwner(), adapter::submitList);
  }

  private class Callbacks implements PaymentsHomeAdapter.Callbacks {
    @Override
    public void onPaymentItem(@NonNull PaymentItem model) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(PaymentsPagerItemFragment.this),
                                  PaymentPreferencesDirections.actionDirectlyToPaymentDetails(model.getPaymentDetailsParcelable()));
    }
  }
}
