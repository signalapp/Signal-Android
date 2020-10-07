package org.thoughtcrime.securesms.recipients.ui.notifications;

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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;

import com.annimon.stream.function.Consumer;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.Objects;

public class CustomNotificationsDialogFragment extends DialogFragment {

  private static final short MESSAGE_RINGTONE_PICKER_REQUEST_CODE = 13562;
  private static final short CALL_RINGTONE_PICKER_REQUEST_CODE    = 23621;

  private static final String ARG_RECIPIENT_ID = "recipient_id";

  private View         customNotificationsRow;
  private SwitchCompat customNotificationsSwitch;
  private View         soundRow;
  private View         soundLabel;
  private TextView     soundSelector;
  private View         messageVibrateRow;
  private View         messageVibrateLabel;
  private TextView     messageVibrateSelector;
  private SwitchCompat messageVibrateSwitch;
  private View         callHeading;
  private View         ringtoneRow;
  private TextView     ringtoneSelector;
  private View         callVibrateRow;
  private TextView     callVibrateSelector;

  private CustomNotificationsViewModel viewModel;

  public static DialogFragment create(@NonNull RecipientId recipientId) {
    DialogFragment fragment = new CustomNotificationsDialogFragment();
    Bundle         args     = new Bundle();

    args.putParcelable(ARG_RECIPIENT_ID, recipientId);
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (ThemeUtil.isDarkTheme(requireActivity())) {
      setStyle(STYLE_NO_FRAME, R.style.TextSecure_DarkTheme_AnimatedDialog);
    } else {
      setStyle(STYLE_NO_FRAME, R.style.TextSecure_LightTheme_AnimatedDialog);
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
    if (resultCode == Activity.RESULT_OK && data != null) {
      Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
      if (requestCode == MESSAGE_RINGTONE_PICKER_REQUEST_CODE) {
        viewModel.setMessageSound(uri);
      } else if (requestCode == CALL_RINGTONE_PICKER_REQUEST_CODE) {
        viewModel.setCallSound(uri);
      }
    }
  }

  private void initializeViewModel() {
    Bundle                               arguments   = requireArguments();
    RecipientId                          recipientId = Objects.requireNonNull(arguments.getParcelable(ARG_RECIPIENT_ID));
    CustomNotificationsRepository        repository  = new CustomNotificationsRepository(requireContext(), recipientId);
    CustomNotificationsViewModel.Factory factory     = new CustomNotificationsViewModel.Factory(recipientId, repository);

    viewModel = ViewModelProviders.of(this, factory).get(CustomNotificationsViewModel.class);
  }

  private void initializeViews(@NonNull View view) {
    customNotificationsRow    = view.findViewById(R.id.custom_notifications_row);
    customNotificationsSwitch = view.findViewById(R.id.custom_notifications_enable_switch);
    soundRow                  = view.findViewById(R.id.custom_notifications_sound_row);
    soundLabel                = view.findViewById(R.id.custom_notifications_sound_label);
    soundSelector             = view.findViewById(R.id.custom_notifications_sound_selection);
    messageVibrateSwitch      = view.findViewById(R.id.custom_notifications_vibrate_switch);
    messageVibrateRow         = view.findViewById(R.id.custom_notifications_message_vibrate_row);
    messageVibrateLabel       = view.findViewById(R.id.custom_notifications_message_vibrate_label);
    messageVibrateSelector    = view.findViewById(R.id.custom_notifications_message_vibrate_selector);
    callHeading               = view.findViewById(R.id.custom_notifications_call_settings_section_header);
    ringtoneRow               = view.findViewById(R.id.custom_notifications_ringtone_row);
    ringtoneSelector          = view.findViewById(R.id.custom_notifications_ringtone_selection);
    callVibrateRow            = view.findViewById(R.id.custom_notifications_call_vibrate_row);
    callVibrateSelector       = view.findViewById(R.id.custom_notifications_call_vibrate_selectior);

    Toolbar toolbar = view.findViewById(R.id.custom_notifications_toolbar);

    toolbar.setNavigationOnClickListener(v -> dismissAllowingStateLoss());

    CompoundButton.OnCheckedChangeListener onCustomNotificationsSwitchCheckChangedListener = (buttonView, isChecked) -> {
      viewModel.setHasCustomNotifications(isChecked);
    };

    viewModel.isInitialLoadComplete().observe(getViewLifecycleOwner(), customNotificationsSwitch::setEnabled);

    if (NotificationChannels.supported()) {
      viewModel.hasCustomNotifications().observe(getViewLifecycleOwner(), hasCustomNotifications -> {
        if (customNotificationsSwitch.isChecked() != hasCustomNotifications) {
          customNotificationsSwitch.setOnCheckedChangeListener(null);
          customNotificationsSwitch.setChecked(hasCustomNotifications);
        }

        customNotificationsSwitch.setOnCheckedChangeListener(onCustomNotificationsSwitchCheckChangedListener);
        customNotificationsRow.setOnClickListener(v -> customNotificationsSwitch.toggle());

        soundRow.setEnabled(hasCustomNotifications);
        soundLabel.setEnabled(hasCustomNotifications);
        messageVibrateRow.setEnabled(hasCustomNotifications);
        messageVibrateLabel.setEnabled(hasCustomNotifications);
        soundSelector.setVisibility(hasCustomNotifications ? View.VISIBLE : View.GONE);
        messageVibrateSwitch.setVisibility(hasCustomNotifications ? View.VISIBLE : View.GONE);
      });

      messageVibrateSelector.setVisibility(View.GONE);
      messageVibrateSwitch.setVisibility(View.VISIBLE);

      messageVibrateRow.setOnClickListener(v -> messageVibrateSwitch.toggle());

      CompoundButton.OnCheckedChangeListener onVibrateSwitchCheckChangedListener = (buttonView, isChecked) -> viewModel.setMessageVibrate(RecipientDatabase.VibrateState.fromBoolean(isChecked));

      viewModel.getMessageVibrateToggle().observe(getViewLifecycleOwner(), vibrateEnabled -> {
        if (messageVibrateSwitch.isChecked() != vibrateEnabled) {
          messageVibrateSwitch.setOnCheckedChangeListener(null);
          messageVibrateSwitch.setChecked(vibrateEnabled);
        }

        messageVibrateSwitch.setOnCheckedChangeListener(onVibrateSwitchCheckChangedListener);
      });
    } else {
      customNotificationsRow.setVisibility(View.GONE);

      messageVibrateSwitch.setVisibility(View.GONE);
      messageVibrateSelector.setVisibility(View.VISIBLE);

      soundRow.setEnabled(true);
      soundLabel.setEnabled(true);
      messageVibrateRow.setEnabled(true);
      messageVibrateLabel.setEnabled(true);
      soundSelector.setVisibility(View.VISIBLE);

      viewModel.getMessageVibrateState().observe(getViewLifecycleOwner(), vibrateState -> presentVibrateState(vibrateState, this.messageVibrateRow, this.messageVibrateSelector, (w) -> viewModel.setMessageVibrate(w)));
    }

    viewModel.getNotificationSound().observe(getViewLifecycleOwner(), sound -> {
      soundSelector.setText(getRingtoneSummary(requireContext(), sound, Settings.System.DEFAULT_NOTIFICATION_URI));
      soundSelector.setTag(sound);
      soundRow.setOnClickListener(v -> launchSoundSelector(sound, false));
    });

    viewModel.getShowCallingOptions().observe(getViewLifecycleOwner(), showCalling -> {
      callHeading.setVisibility(showCalling ? View.VISIBLE : View.GONE);
      ringtoneRow.setVisibility(showCalling ? View.VISIBLE : View.GONE);
      callVibrateRow.setVisibility(showCalling ? View.VISIBLE : View.GONE);
    });

    viewModel.getRingtone().observe(getViewLifecycleOwner(), sound -> {
      ringtoneSelector.setText(getRingtoneSummary(requireContext(), sound, Settings.System.DEFAULT_RINGTONE_URI));
      ringtoneSelector.setTag(sound);
      ringtoneRow.setOnClickListener(v -> launchSoundSelector(sound, true));
    });

    viewModel.getCallingVibrateState().observe(getViewLifecycleOwner(), vibrateState -> presentVibrateState(vibrateState, this.callVibrateRow, this.callVibrateSelector, (w) -> viewModel.setCallingVibrate(w)));
  }

  private void presentVibrateState(@NonNull RecipientDatabase.VibrateState vibrateState,
                                   @NonNull View vibrateRow,
                                   @NonNull TextView vibrateSelector,
                                   @NonNull Consumer<RecipientDatabase.VibrateState> onSelect)
  {
    vibrateSelector.setText(getVibrateSummary(requireContext(), vibrateState));
    vibrateRow.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                                                      .setTitle(R.string.CustomNotificationsDialogFragment__vibrate)
                                                      .setSingleChoiceItems(R.array.recipient_vibrate_entries, vibrateState.ordinal(), ((dialog, which) -> {
                                                         onSelect.accept(RecipientDatabase.VibrateState.fromId(which));
                                                         dialog.dismiss();
                                                      }))
                                                      .setNegativeButton(android.R.string.cancel, null)
                                                      .show());
  }

  private @NonNull String getRingtoneSummary(@NonNull Context context, @Nullable Uri ringtone, @Nullable Uri defaultNotificationUri) {
    if (ringtone == null || ringtone.equals(defaultNotificationUri)) {
      return context.getString(R.string.CustomNotificationsDialogFragment__default);
    } else if (ringtone.toString().isEmpty()) {
      return context.getString(R.string.preferences__silent);
    } else {
      Ringtone tone = RingtoneManager.getRingtone(getActivity(), ringtone);

      if (tone != null) {
        return tone.getTitle(context);
      }
    }

    return context.getString(R.string.CustomNotificationsDialogFragment__default);
  }

  private void launchSoundSelector(@Nullable Uri current, boolean calls) {
    Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);

    if      (current == null)              current = calls ? Settings.System.DEFAULT_RINGTONE_URI : Settings.System.DEFAULT_NOTIFICATION_URI;
    else if (current.toString().isEmpty()) current = null;

    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, calls ? RingtoneManager.TYPE_RINGTONE : RingtoneManager.TYPE_NOTIFICATION);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultSound(calls));

    startActivityForResult(intent, calls ? CALL_RINGTONE_PICKER_REQUEST_CODE : MESSAGE_RINGTONE_PICKER_REQUEST_CODE);
  }

  private Uri defaultSound(boolean calls) {
    Uri defaultValue;

    if (calls) defaultValue = TextSecurePreferences.getCallNotificationRingtone(requireContext());
    else       defaultValue = TextSecurePreferences.getNotificationRingtone(requireContext());
    return defaultValue;
  }

  private static @NonNull String getVibrateSummary(@NonNull Context context, @NonNull RecipientDatabase.VibrateState vibrateState) {
    switch (vibrateState) {
      case DEFAULT  : return context.getString(R.string.CustomNotificationsDialogFragment__default);
      case ENABLED  : return context.getString(R.string.CustomNotificationsDialogFragment__enabled);
      case DISABLED : return context.getString(R.string.CustomNotificationsDialogFragment__disabled);
      default       : throw new AssertionError();
    }
  }
}
