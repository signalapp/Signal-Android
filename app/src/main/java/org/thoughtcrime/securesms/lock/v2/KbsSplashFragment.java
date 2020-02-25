package org.thoughtcrime.securesms.lock.v2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;

public final class KbsSplashFragment extends Fragment {

  private TextView title;
  private TextView description;
  private TextView primaryAction;
  private TextView secondaryAction;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.kbs_splash_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    title           = view.findViewById(R.id.kbs_splash_title);
    description     = view.findViewById(R.id.kbs_splash_description);
    primaryAction   = view.findViewById(R.id.kbs_splash_primary_action);
    secondaryAction = view.findViewById(R.id.kbs_splash_secondary_action);

    primaryAction.setOnClickListener(v -> onCreatePin());
    secondaryAction.setOnClickListener(v -> onLearnMore());

    if (PinUtil.userHasPin(requireContext())) {
      setUpRegLockEnabled();
    } else {
      setUpRegLockDisabled();
    }

    description.setMovementMethod(LinkMovementMethod.getInstance());

    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() { }
    });
  }

  private void setUpRegLockEnabled() {
    title.setText(R.string.KbsSplashFragment__registration_lock_equals_pin);
    description.setText(R.string.KbsSplashFragment__your_registration_lock_is_now_called_a_pin);
    primaryAction.setText(R.string.KbsSplashFragment__update_pin);
    secondaryAction.setText(R.string.KbsSplashFragment__learn_more);
  }

  private void setUpRegLockDisabled() {
    title.setText(R.string.KbsSplashFragment__introducing_pins);
    description.setText(R.string.KbsSplashFragment__pins_add_another_level_of_security_to_your_account);
    primaryAction.setText(R.string.KbsSplashFragment__create_your_pin);
    secondaryAction.setText(R.string.KbsSplashFragment__learn_more);
  }

  private void onCreatePin() {
    KbsSplashFragmentDirections.ActionCreateKbsPin action = KbsSplashFragmentDirections.actionCreateKbsPin();

    action.setIsPinChange(PinUtil.userHasPin(requireContext()));

    Navigation.findNavController(requireView()).navigate(action);
  }

  private void onLearnMore() {
    Intent intent = new Intent(Intent.ACTION_VIEW);

    intent.setData(Uri.parse(getString(R.string.KbsSplashFragment__learn_more_link)));

    startActivity(intent);
  }
}
