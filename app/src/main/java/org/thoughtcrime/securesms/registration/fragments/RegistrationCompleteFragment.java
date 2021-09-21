package org.thoughtcrime.securesms.registration.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.ActivityNavigator;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileContentUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.pin.PinRestoreActivity;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.registration.RegistrationUtil;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;

import java.util.Arrays;

public final class RegistrationCompleteFragment extends LoggingFragment {

  private static final String TAG = Log.tag(RegistrationCompleteFragment.class);

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_blank, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    FragmentActivity      activity  = requireActivity();
    RegistrationViewModel viewModel = new ViewModelProvider(activity).get(RegistrationViewModel.class);

    if (SignalStore.storageService().needsAccountRestore()) {
      Log.i(TAG, "Performing pin restore");
      activity.startActivity(new Intent(activity, PinRestoreActivity.class));
    } else if (!viewModel.isReregister()) {
      boolean needsProfile = Recipient.self().getProfileName().isEmpty() || !AvatarHelper.hasAvatar(activity, Recipient.self().getId());
      boolean needsPin     = !SignalStore.kbsValues().hasPin();

      Log.i(TAG, "Pin restore flow not required." +
                 " profile name: "   + Recipient.self().getProfileName().isEmpty() +
                 " profile avatar: " + !AvatarHelper.hasAvatar(activity, Recipient.self().getId()) +
                 " needsPin:"        + needsPin);

      Intent startIntent = MainActivity.clearTop(activity);

      if (needsPin) {
        startIntent = chainIntents(CreateKbsPinActivity.getIntentForPinCreate(requireContext()), startIntent);
      }

      if (needsProfile) {
        startIntent = chainIntents(EditProfileActivity.getIntentForUserProfile(activity), startIntent);
      }

      if (!needsProfile && !needsPin) {
        ApplicationDependencies.getJobManager()
                               .startChain(new ProfileUploadJob())
                               .then(Arrays.asList(new MultiDeviceProfileKeyUpdateJob(), new MultiDeviceProfileContentUpdateJob()))
                               .enqueue();

        RegistrationUtil.maybeMarkRegistrationComplete(requireContext());
      }

      activity.startActivity(startIntent);
    }

    activity.finish();
    ActivityNavigator.applyPopAnimationsToPendingTransition(activity);
  }

  private static @NonNull Intent chainIntents(@NonNull Intent sourceIntent, @NonNull Intent nextIntent) {
    sourceIntent.putExtra("next_intent", nextIntent);
    return sourceIntent;
  }
}
