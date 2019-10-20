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

import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.CreateProfileActivity;
import org.thoughtcrime.securesms.R;

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
      activity.startActivity(getRoutedIntent(activity, CreateProfileActivity.class, new Intent(activity, ConversationListActivity.class)));
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
