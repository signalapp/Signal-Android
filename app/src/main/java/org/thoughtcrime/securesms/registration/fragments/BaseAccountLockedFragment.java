package org.thoughtcrime.securesms.registration.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.registration.viewmodel.BaseRegistrationViewModel;

import java.util.concurrent.TimeUnit;

/**
 * Base fragment used by registration and change number flow to show an account as locked.
 */
public abstract class BaseAccountLockedFragment extends LoggingFragment {

  public BaseAccountLockedFragment(int contentLayoutId) {
    super(contentLayoutId);
  }

  @Override
  @CallSuper
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    TextView description = view.findViewById(R.id.account_locked_description);

    BaseRegistrationViewModel viewModel = getViewModel();
    viewModel.getLockedTimeRemaining().observe(getViewLifecycleOwner(),
      t -> description.setText(getString(R.string.AccountLockedFragment__your_account_has_been_locked_to_protect_your_privacy, durationToDays(t)))
    );

    view.findViewById(R.id.account_locked_next).setOnClickListener(v -> onNext());
    view.findViewById(R.id.account_locked_learn_more).setOnClickListener(v -> learnMore());

    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        onNext();
      }
    });
  }

  private void learnMore() {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setData(Uri.parse(getString(R.string.AccountLockedFragment__learn_more_url)));
    startActivity(intent);
  }

  private static long durationToDays(long duration) {
    return duration != 0L ? getLockoutDays(duration) : 7;
  }

  private static int getLockoutDays(long timeRemainingMs) {
    return (int) TimeUnit.MILLISECONDS.toDays(timeRemainingMs) + 1;
  }

  protected abstract BaseRegistrationViewModel getViewModel();

  protected abstract void onNext();
}
