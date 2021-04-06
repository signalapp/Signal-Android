package org.thoughtcrime.securesms.payments.preferences;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.navigation.Navigation;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;

public class PaymentsAllActivityFragment extends LoggingFragment {

  public PaymentsAllActivityFragment() {
    super(R.layout.payments_activity_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    ViewPager viewPager = view.findViewById(R.id.payments_all_activity_fragment_view_pager);
    TabLayout tabLayout = view.findViewById(R.id.payments_all_activity_fragment_tabs);
    Toolbar   toolbar   = view.findViewById(R.id.payments_all_activity_fragment_toolbar);

    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    viewPager.setAdapter(new Adapter(getChildFragmentManager()));
    tabLayout.setupWithViewPager(viewPager);
  }

  private final class Adapter extends FragmentStatePagerAdapter {

    Adapter(@NonNull FragmentManager fm) {
      super(fm, FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
    }

    @Override
    public @NonNull CharSequence getPageTitle(int position) {
      switch (position) {
        case 0 : return getString(R.string.PaymentsAllActivityFragment__all);
        case 1 : return getString(R.string.PaymentsAllActivityFragment__sent);
        case 2 : return getString(R.string.PaymentsAllActivityFragment__received);
        default: throw new IllegalStateException("Unknown position: " + position);
      }
    }

    @Override
    public @NonNull Fragment getItem(int position) {
      switch (position) {
        case 0 : return PaymentsPagerItemFragment.getFragmentForAllPayments();
        case 1 : return PaymentsPagerItemFragment.getFragmentForSentPayments();
        case 2 : return PaymentsPagerItemFragment.getFragmentForReceivedPayments();
        default: throw new IllegalStateException("Unknown position: " + position);
      }
    }

    @Override
    public int getCount() {
      return 3;
    }
  }
}
