package org.thoughtcrime.securesms.registration.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.ActivityNavigator;

import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;

public final class RegistrationCompleteFragment extends BaseRegistrationFragment {

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_blank, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    FragmentActivity activity = requireActivity();

    if (!isReregister()) {
      Intent setProfileNameIntent = getRoutedIntent(activity, EditProfileActivity.class, new Intent(activity, MainActivity.class));

      setProfileNameIntent.putExtra(EditProfileActivity.SHOW_BACK_ARROW, false);

      activity.startActivity(setProfileNameIntent);
    }

    activity.finish();
    ActivityNavigator.applyPopAnimationsToPendingTransition(activity);
  }

  private static Intent getRoutedIntent(@NonNull Context context, Class<?> destination, @Nullable Intent nextIntent) {
    final Intent intent = new Intent(context, destination);
    if (nextIntent != null) intent.putExtra("next_intent", nextIntent);
    return intent;
  }
}
