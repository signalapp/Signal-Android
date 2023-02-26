package org.thoughtcrime.securesms.lock.v2;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.pin.PinOptOutDialog;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

public final class KbsSplashFragment extends Fragment {

  private TextView title;
  private TextView description;
  private TextView primaryAction;
  private TextView secondaryAction;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

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

    if (RegistrationLockUtil.userHasRegistrationLock(requireContext())) {
      setUpRegLockEnabled();
    } else {
      setUpRegLockDisabled();
    }

    description.setMovementMethod(LinkMovementMethod.getInstance());

    Toolbar toolbar = view.findViewById(R.id.kbs_splash_toolbar);
    ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
    ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(null);

    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() { }
    });
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    inflater.inflate(R.menu.pin_skip, menu);
  }

  @Override
  public void onPrepareOptionsMenu(@NonNull Menu menu) {
    if (RegistrationLockUtil.userHasRegistrationLock(requireContext())) {
      menu.clear();
    }
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.menu_pin_learn_more) {
      onLearnMore();
      return true;
    } else if (item.getItemId() == R.id.menu_pin_skip) {
      onPinSkipped();
      return true;
    } else {
      return false;
    }
  }

  private void setUpRegLockEnabled() {
    title.setText(R.string.KbsSplashFragment__registration_lock_equals_pin);
    description.setText(R.string.KbsSplashFragment__your_registration_lock_is_now_called_a_pin);
    primaryAction.setText(R.string.KbsSplashFragment__update_pin);
    secondaryAction.setText(R.string.KbsSplashFragment__learn_more);
  }

  private void setUpRegLockDisabled() {
    title.setText(R.string.KbsSplashFragment__introducing_pins);
    description.setText(R.string.KbsSplashFragment__pins_keep_information_stored_with_signal_encrypted);
    primaryAction.setText(R.string.KbsSplashFragment__create_your_pin);
    secondaryAction.setText(R.string.KbsSplashFragment__learn_more);
  }

  private void onCreatePin() {
    KbsSplashFragmentDirections.ActionCreateKbsPin action = KbsSplashFragmentDirections.actionCreateKbsPin();

    action.setIsPinChange(SignalStore.kbsValues().hasPin());

    SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), action);
  }

  private void onLearnMore() {
    CommunicationActions.openBrowserLink(requireContext(), getString(R.string.KbsSplashFragment__learn_more_link));
  }

  private void onPinSkipped() {
    PinOptOutDialog.show(requireContext(), () -> requireActivity().finish());
  }
}
