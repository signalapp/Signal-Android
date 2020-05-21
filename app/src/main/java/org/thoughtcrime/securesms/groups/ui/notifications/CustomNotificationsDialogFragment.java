package org.thoughtcrime.securesms.groups.ui.notifications;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.util.ThemeUtil;

public class CustomNotificationsDialogFragment extends DialogFragment {

  private static final short RINGTONE_PICKER_REQUEST_CODE = 13562;

  private static final String ARG_GROUP_ID = "group_id";

  private Switch   customNotificationsSwitch;
  private View     soundLabel;
  private TextView soundSelector;
  private View     vibrateLabel;
  private Switch   vibrateSwitch;

  private CustomNotificationsViewModel viewModel;

  public static DialogFragment create(@NonNull GroupId groupId) {
    DialogFragment fragment = new CustomNotificationsDialogFragment();
    Bundle         args     = new Bundle();

    args.putString(ARG_GROUP_ID, groupId.toString());
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (ThemeUtil.isDarkTheme(requireActivity())) {
      setStyle(STYLE_NO_FRAME, R.style.TextSecure_DarkTheme);
    } else {
      setStyle(STYLE_NO_FRAME, R.style.TextSecure_LightTheme);
    }
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.custom_notifications_dialog_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initializeViewModel();
    initializeViews(view);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == RINGTONE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
      Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

      viewModel.setMessageSound(uri);
    }
  }

  private void initializeViewModel() {
    Bundle                               arguments  = requireArguments();
    GroupId                              groupId    = GroupId.parseOrThrow(arguments.getString(ARG_GROUP_ID, ""));
    CustomNotificationsRepository        repository = new CustomNotificationsRepository(requireContext(), groupId);
    CustomNotificationsViewModel.Factory factory    = new CustomNotificationsViewModel.Factory(groupId, repository);

    viewModel = ViewModelProviders.of(this, factory).get(CustomNotificationsViewModel.class);
  }

  private void initializeViews(@NonNull View view) {
    customNotificationsSwitch = view.findViewById(R.id.custom_notifications_enable_switch);
    soundLabel                = view.findViewById(R.id.custom_notifications_sound_label);
    soundSelector             = view.findViewById(R.id.custom_notifications_sound_selection);
    vibrateLabel              = view.findViewById(R.id.custom_notifications_vibrate_label);
    vibrateSwitch             = view.findViewById(R.id.custom_notifications_vibrate_switch);

    Toolbar toolbar = view.findViewById(R.id.custom_notifications_toolbar);

    toolbar.setNavigationOnClickListener(v -> requireActivity().finish());

    CompoundButton.OnCheckedChangeListener onCustomNotificationsSwitchCheckChangedListener = (buttonView, isChecked) -> {
      viewModel.setHasCustomNotifications(isChecked);
    };

    viewModel.isInitialLoadComplete().observe(getViewLifecycleOwner(), customNotificationsSwitch::setEnabled);

    viewModel.hasCustomNotifications().observe(getViewLifecycleOwner(), hasCustomNotifications -> {
      if (customNotificationsSwitch.isChecked() != hasCustomNotifications) {
        customNotificationsSwitch.setOnCheckedChangeListener(null);
        customNotificationsSwitch.setChecked(hasCustomNotifications);
      }

      customNotificationsSwitch.setOnCheckedChangeListener(onCustomNotificationsSwitchCheckChangedListener);

      soundLabel.setEnabled(hasCustomNotifications);
      vibrateLabel.setEnabled(hasCustomNotifications);
      soundSelector.setVisibility(hasCustomNotifications ? View.VISIBLE : View.GONE);
      vibrateSwitch.setVisibility(hasCustomNotifications ? View.VISIBLE : View.GONE);
    });

    CompoundButton.OnCheckedChangeListener onVibrateSwitchCheckChangedListener = (buttonView, isChecked) -> {
      viewModel.setMessageVibrate(isChecked ? RecipientDatabase.VibrateState.ENABLED : RecipientDatabase.VibrateState.DISABLED);
    };

    viewModel.getVibrateState().observe(getViewLifecycleOwner(), vibrateState -> {
      boolean vibrateEnabled = vibrateState != RecipientDatabase.VibrateState.DISABLED;

      if (vibrateSwitch.isChecked() != vibrateEnabled) {
        vibrateSwitch.setOnCheckedChangeListener(null);
        vibrateSwitch.setChecked(vibrateEnabled);
      }

      vibrateSwitch.setOnCheckedChangeListener(onVibrateSwitchCheckChangedListener);
    });

    viewModel.getNotificationSound().observe(getViewLifecycleOwner(), sound -> {
      soundSelector.setText(getRingtoneSummary(requireContext(), sound));
      soundSelector.setTag(sound);
    });

    soundSelector.setOnClickListener(v -> launchSoundSelector(viewModel.getNotificationSound().getValue()));
  }

  private @NonNull String getRingtoneSummary(@NonNull Context context, @Nullable Uri ringtone) {
    if (ringtone == null) {
      return context.getString(R.string.preferences__default);
    } else if (ringtone.toString().isEmpty()) {
      return context.getString(R.string.preferences__silent);
    } else {
      Ringtone tone = RingtoneManager.getRingtone(getActivity(), ringtone);

      if (tone != null) {
        return tone.getTitle(context);
      }
    }

    return context.getString(R.string.preferences__default);
  }

  private void launchSoundSelector(@Nullable Uri current) {
    Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);

    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current);

    startActivityForResult(intent, RINGTONE_PICKER_REQUEST_CODE);
  }
}
