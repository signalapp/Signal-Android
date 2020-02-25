package org.thoughtcrime.securesms.registration.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;

import java.util.concurrent.TimeUnit;

public class AccountLockedFragment extends BaseRegistrationFragment {

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.account_locked_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    TextView description = view.findViewById(R.id.account_locked_description);

    getModel().getTimeRemaining().observe(getViewLifecycleOwner(),
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

  private static long durationToDays(Long duration) {
    return duration != null ? getLockoutDays(duration) : 7;
  }

  private static int getLockoutDays(long timeRemainingMs) {
    return (int) TimeUnit.MILLISECONDS.toDays(timeRemainingMs) + 1;
  }

  private void onNext() {
    requireActivity().finish();
  }
}
