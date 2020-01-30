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
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.R;

public class AccountLockedFragment extends Fragment {

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.account_locked_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    AccountLockedFragmentArgs args = AccountLockedFragmentArgs.fromBundle(requireArguments());

    TextView description = view.findViewById(R.id.account_locked_description);

    description.setText(getString(R.string.AccountLockedFragment__your_account_has_been_locked_to_protect_your_privacy, args.getTimeRemaining()));

    view.findViewById(R.id.account_locked_next).setOnClickListener(this::onNextClicked);
    view.findViewById(R.id.account_locked_learn_more).setOnClickListener(this::onLearnMoreClicked);

    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        onNext();
      }
    });
  }

  private void onNextClicked(@NonNull View unused) {
    onNext();
  }

  private void onLearnMoreClicked(@NonNull View unused) {
    Intent intent = new Intent(Intent.ACTION_VIEW);

    intent.setData(Uri.parse(getString(R.string.AccountLockedFragment__learn_more_url)));

    startActivity(intent);
  }

  private void onNext() {
    requireActivity().finish();
  }
}
