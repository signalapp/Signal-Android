package org.thoughtcrime.securesms.conversation.ui.error;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.ConversationFragment;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.util.DeviceProperties;

/**
 * Provide basic steps to fix potential call notification issues based on what we can detect on the system
 * and app settings.
 */
@TargetApi(26)
public final class EnableCallNotificationSettingsDialog extends DialogFragment {

  private static final String TAG          = Log.tag(EnableCallNotificationSettingsDialog.class);
  private static final String FRAGMENT_TAG = "MissedCallCheckSettingsDialog";

  private static final int NOTIFICATIONS_DISABLED      = 1 << 1;
  private static final int CALL_NOTIFICATIONS_DISABLED = 1 << 2;
  private static final int CALL_CHANNEL_INVALID        = 1 << 4;
  private static final int BACKGROUND_RESTRICTED       = 1 << 8;

  private View view;

  public static boolean shouldShow(@NonNull Context context) {
    return getCallNotificationSettingsBitmask(context) != 0;
  }

  public static void fixAutomatically(@NonNull Context context) {
    if (areCallNotificationsDisabled(context)) {
      SignalStore.settings().setCallNotificationsEnabled(true);
      Toast.makeText(context, R.string.EnableCallNotificationSettingsDialog__call_notifications_enabled, Toast.LENGTH_SHORT).show();
    }
  }

  public static void show(@NonNull FragmentManager fragmentManager) {
    if (fragmentManager.findFragmentByTag(FRAGMENT_TAG) != null) {
      Log.i(TAG, "Dialog already being shown");
      return;
    }

    new EnableCallNotificationSettingsDialog().show(fragmentManager, FRAGMENT_TAG);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Signal_MaterialAlertDialog);

    Runnable action = null;
    switch (getCallNotificationSettingsBitmask(requireContext())) {
      case NOTIFICATIONS_DISABLED:
        dialogBuilder.setTitle(R.string.EnableCallNotificationSettingsDialog__enable_call_notifications)
                     .setMessage(R.string.EnableCallNotificationSettingsDialog__to_receive_call_notifications_tap_settings_and_turn_on_show_notifications)
                     .setPositiveButton(R.string.EnableCallNotificationSettingsDialog__settings, null);
        action = this::showNotificationSettings;
        break;
      case CALL_CHANNEL_INVALID:
        dialogBuilder.setTitle(R.string.EnableCallNotificationSettingsDialog__enable_call_notifications)
                     .setMessage(R.string.EnableCallNotificationSettingsDialog__to_receive_call_notifications_tap_settings_and_turn_on_notifications)
                     .setPositiveButton(R.string.EnableCallNotificationSettingsDialog__settings, null);
        action = this::showNotificationChannelSettings;
        break;
      case BACKGROUND_RESTRICTED:
        dialogBuilder.setTitle(R.string.EnableCallNotificationSettingsDialog__enable_background_activity)
                     .setMessage(R.string.EnableCallNotificationSettingsDialog__to_receive_call_notifications_tap_settings_and_enable_background_activity_in_battery_settings)
                     .setPositiveButton(R.string.EnableCallNotificationSettingsDialog__settings, null);
        action = this::showAppSettings;
        break;
      default:
        dialogBuilder.setTitle(R.string.EnableCallNotificationSettingsDialog__enable_call_notifications)
                     .setView(createView())
                     .setPositiveButton(android.R.string.ok, null);
        break;
    }

    dialogBuilder.setNegativeButton(android.R.string.cancel, null);

    AlertDialog dialog = dialogBuilder.create();

    if (action != null) {
      final Runnable localAction = action;
      dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> localAction.run()));
    }

    return dialog;
  }

  @Override
  public void onResume() {
    super.onResume();
    if (getCallNotificationSettingsBitmask(requireContext()) == 0) {
      dismissAllowingStateLoss();
    } else if (view != null) {
      bind(view);
    }
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);
    if (getParentFragment() instanceof ConversationFragment) {
      ((ConversationFragment) getParentFragment()).refreshList();
    }
  }

  @SuppressLint("InflateParams")
  private @NonNull View createView() {
    view = LayoutInflater.from(getContext()).inflate(R.layout.enable_call_notification_settings_dialog_fragment, null, false);
    bind(view);
    return view;
  }

  private void bind(@NonNull View view) {
    TextView           allConfigured                 = view.findViewById(R.id.enable_call_notification_settings_dialog_system_all_configured);
    AppCompatImageView systemSettingIndicator        = view.findViewById(R.id.enable_call_notification_settings_dialog_system_setting_indicator);
    TextView           systemSettingText             = view.findViewById(R.id.enable_call_notification_settings_dialog_system_setting_text);
    AppCompatImageView channelSettingIndicator       = view.findViewById(R.id.enable_call_notification_settings_dialog_channel_setting_indicator);
    TextView           channelSettingText            = view.findViewById(R.id.enable_call_notification_settings_dialog_channel_setting_text);
    AppCompatImageView backgroundRestrictedIndicator = view.findViewById(R.id.enable_call_notification_settings_dialog_background_restricted_indicator);
    TextView           backgroundRestrictedText      = view.findViewById(R.id.enable_call_notification_settings_dialog_background_restricted_text);

    if (areNotificationsDisabled(requireContext())) {
      systemSettingIndicator.setVisibility(View.VISIBLE);
      systemSettingText.setVisibility(View.VISIBLE);
      systemSettingText.setOnClickListener(v -> showNotificationSettings());
    } else {
      systemSettingIndicator.setVisibility(View.GONE);
      systemSettingText.setVisibility(View.GONE);
      systemSettingText.setOnClickListener(null);
    }

    if (isCallChannelInvalid(requireContext())) {
      channelSettingIndicator.setVisibility(View.VISIBLE);
      channelSettingText.setVisibility(View.VISIBLE);
      channelSettingText.setOnClickListener(v -> showNotificationChannelSettings());
    } else {
      channelSettingIndicator.setVisibility(View.GONE);
      channelSettingText.setVisibility(View.GONE);
      channelSettingText.setOnClickListener(null);
    }

    if (isBackgroundRestricted(requireContext())) {
      backgroundRestrictedIndicator.setVisibility(View.VISIBLE);
      backgroundRestrictedText.setVisibility(View.VISIBLE);
      backgroundRestrictedText.setOnClickListener(v -> showAppSettings());
    } else {
      backgroundRestrictedIndicator.setVisibility(View.GONE);
      backgroundRestrictedText.setVisibility(View.GONE);
      backgroundRestrictedText.setOnClickListener(null);
    }

    allConfigured.setVisibility(shouldShow(requireContext()) ? View.GONE : View.VISIBLE);
  }

  private void showNotificationSettings() {
    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
    startActivity(intent);
  }

  private void showNotificationChannelSettings() {
    NotificationChannels.getInstance().openChannelSettings(NotificationChannels.CALLS, null);
  }

  private void showAppSettings() {
    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                               Uri.fromParts("package", requireContext().getPackageName(), null));
    startActivity(intent);
  }

  private static boolean areNotificationsDisabled(@NonNull Context context) {
    return !NotificationChannels.getInstance().areNotificationsEnabled();
  }

  private static boolean areCallNotificationsDisabled(Context context) {
    return !SignalStore.settings().isCallNotificationsEnabled();
  }

  private static boolean isCallChannelInvalid(Context context) {
    return !NotificationChannels.getInstance().isCallsChannelValid();
  }

  private static boolean isBackgroundRestricted(Context context) {
    return Build.VERSION.SDK_INT >= 28 && DeviceProperties.isBackgroundRestricted(context);
  }

  private static int getCallNotificationSettingsBitmask(@NonNull Context context) {
    int bitmask = 0;

    if (areNotificationsDisabled(context)) {
      bitmask |= NOTIFICATIONS_DISABLED;
    }

    if (areCallNotificationsDisabled(context)) {
      bitmask |= CALL_NOTIFICATIONS_DISABLED;
    }

    if (isCallChannelInvalid(context)) {
      bitmask |= CALL_CHANNEL_INVALID;
    }

    if (isBackgroundRestricted(context)) {
      bitmask |= BACKGROUND_RESTRICTED;
    }

    return bitmask;
  }
}
