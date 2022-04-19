package org.thoughtcrime.securesms.preferences;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;

import org.session.libsession.utilities.TextSecurePreferences;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.CallNotificationBuilder;
import org.thoughtcrime.securesms.util.IntentUtils;

import java.util.concurrent.TimeUnit;

import kotlin.jvm.functions.Function1;
import mobi.upod.timedurationpicker.TimeDurationPickerDialog;
import network.loki.messenger.BuildConfig;
import network.loki.messenger.R;

public class AppProtectionPreferenceFragment extends CorrectedPreferenceFragment {

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
  }

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    this.findPreference(TextSecurePreferences.SCREEN_LOCK).setOnPreferenceChangeListener(new ScreenLockListener());
    this.findPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT).setOnPreferenceClickListener(new ScreenLockTimeoutListener());

    this.findPreference(TextSecurePreferences.READ_RECEIPTS_PREF).setOnPreferenceChangeListener(new ReadReceiptToggleListener());
    this.findPreference(TextSecurePreferences.TYPING_INDICATORS).setOnPreferenceChangeListener(new TypingIndicatorsToggleListener());
    this.findPreference(TextSecurePreferences.LINK_PREVIEWS).setOnPreferenceChangeListener(new LinkPreviewToggleListener());
    this.findPreference(TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED).setOnPreferenceChangeListener(new CallToggleListener(this, this::setCall));

    initializeVisibility();
  }

  private Void setCall(boolean isEnabled) {
    ((SwitchPreferenceCompat)findPreference(TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED)).setChecked(isEnabled);
    if (isEnabled && !CallNotificationBuilder.areNotificationsEnabled(requireActivity())) {
      // show a dialog saying that calls won't work properly if you don't have notifications on at a system level
      new AlertDialog.Builder(requireActivity())
              .setTitle(R.string.CallNotificationBuilder_system_notification_title)
              .setMessage(R.string.CallNotificationBuilder_system_notification_message)
              .setPositiveButton(R.string.activity_notification_settings_title, (d, w) -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                  Intent settingsIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                          .putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID);
                  if (IntentUtils.isResolvable(requireContext(), settingsIntent)) {
                    startActivity(settingsIntent);
                  }
                } else {
                  Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                          .setData(Uri.parse("package:"+BuildConfig.APPLICATION_ID));
                  if (IntentUtils.isResolvable(requireContext(), settingsIntent)) {
                    startActivity(settingsIntent);
                  }
                }
                d.dismiss();
              })
              .setNeutralButton(R.string.dismiss, (d, w) -> {
                // do nothing, user might have broken notifications
                d.dismiss();
              })
              .show();
    }
    return null;
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_app_protection);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (TextSecurePreferences.isPasswordDisabled(getContext())) {
      initializeScreenLockTimeoutSummary();
    }
  }

  private void initializeScreenLockTimeoutSummary() {
    long timeoutSeconds = TextSecurePreferences.getScreenLockTimeout(getContext());
    long hours          = TimeUnit.SECONDS.toHours(timeoutSeconds);
    long minutes        = TimeUnit.SECONDS.toMinutes(timeoutSeconds) - (TimeUnit.SECONDS.toHours(timeoutSeconds) * 60  );
    long seconds        = TimeUnit.SECONDS.toSeconds(timeoutSeconds) - (TimeUnit.SECONDS.toMinutes(timeoutSeconds) * 60);

    findPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT)
        .setSummary(timeoutSeconds <= 0 ? getString(R.string.AppProtectionPreferenceFragment_none) :
                                          String.format("%02d:%02d:%02d", hours, minutes, seconds));
  }

  private void initializeVisibility() {
    if (TextSecurePreferences.isPasswordDisabled(getContext())) {
      KeyguardManager keyguardManager = (KeyguardManager)getContext().getSystemService(Context.KEYGUARD_SERVICE);
      if (!keyguardManager.isKeyguardSecure()) {
        ((SwitchPreferenceCompat)findPreference(TextSecurePreferences.SCREEN_LOCK)).setChecked(false);
        findPreference(TextSecurePreferences.SCREEN_LOCK).setEnabled(false);
      }
    } else {
      findPreference(TextSecurePreferences.SCREEN_LOCK).setVisible(false);
      findPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT).setVisible(false);
    }
  }

  private class ScreenLockListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean enabled = (Boolean)newValue;

      TextSecurePreferences.setScreenLockEnabled(getContext(), enabled);

      Intent intent = new Intent(getContext(), KeyCachingService.class);
      intent.setAction(KeyCachingService.LOCK_TOGGLED_EVENT);
      getContext().startService(intent);
      return true;
    }
  }

  private class ScreenLockTimeoutListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      new TimeDurationPickerDialog(getContext(), (view, duration) -> {
        if (duration == 0) {
          TextSecurePreferences.setScreenLockTimeout(getContext(), 0);
        } else {
          long timeoutSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
          TextSecurePreferences.setScreenLockTimeout(getContext(), timeoutSeconds);
        }

        initializeScreenLockTimeoutSummary();
      }, 0).show();

      return true;
    }
  }

  private class ReadReceiptToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      return true;
    }
  }

  private class TypingIndicatorsToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean enabled = (boolean)newValue;

      if (!enabled) {
        ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().clear();
      }

      return true;
    }
  }

  private class LinkPreviewToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      return true;
    }
  }

  private class CallToggleListener implements Preference.OnPreferenceChangeListener {

    private final Fragment context;
    private final Function1<Boolean, Void> setCallback;

    private CallToggleListener(Fragment context, Function1<Boolean,Void> setCallback) {
      this.context = context;
      this.setCallback = setCallback;
    }

    private void requestMicrophonePermission() {
      Permissions.with(context)
              .request(Manifest.permission.RECORD_AUDIO)
              .onAllGranted(() -> {
                TextSecurePreferences.setBooleanPreference(context.requireContext(), TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED, true);
                setCallback.invoke(true);
              })
              .onAnyDenied(() -> setCallback.invoke(false))
              .execute();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean val = (boolean) newValue;
      if (val) {
        // check if we've shown the info dialog and check for microphone permissions
        if (TextSecurePreferences.setShownCallWarning(context.requireContext())) {
          new AlertDialog.Builder(context.requireContext())
                  .setTitle(R.string.dialog_voice_video_title)
                  .setMessage(R.string.dialog_voice_video_message)
                  .setPositiveButton(R.string.dialog_link_preview_enable_button_title, (d, w) -> {
                    requestMicrophonePermission();
                  })
                  .setNegativeButton(R.string.cancel, (d, w) -> {

                  })
                  .show();
        } else {
          requestMicrophonePermission();
        }
        return false;
      } else {
        return true;
      }
    }
  }

}
