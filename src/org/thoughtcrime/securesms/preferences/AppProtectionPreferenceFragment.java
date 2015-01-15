package org.thoughtcrime.securesms.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v4.preference.PreferenceFragment;
import android.util.Log;
import android.widget.Toast;

import com.doomonafireball.betterpickers.hmspicker.HmsPickerBuilder;
import com.doomonafireball.betterpickers.hmspicker.HmsPickerDialogFragment;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.PassphraseChangeActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class AppProtectionPreferenceFragment extends PreferenceFragment {
  private CheckBoxPreference disablePassphrase;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_app_protection);

    disablePassphrase = (CheckBoxPreference) this.findPreference("pref_enable_passphrase_temporary");

    this.findPreference(TextSecurePreferences.CHANGE_PASSPHRASE_PREF)
        .setOnPreferenceClickListener(new ChangePassphraseClickListener());
    this.findPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF)
        .setOnPreferenceClickListener(new PassphraseIntervalClickListener());
    disablePassphrase
        .setOnPreferenceChangeListener(new DisablePassphraseClickListener());
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__app_protection);

    initializePlatformSpecificOptions();
    initializeTimeoutSummary();

    disablePassphrase.setChecked(!TextSecurePreferences.isPasswordDisabled(getActivity()));
  }

  private void initializePlatformSpecificOptions() {
    PreferenceScreen preferenceScreen         = getPreferenceScreen();
    Preference       screenSecurityPreference = findPreference(TextSecurePreferences.SCREEN_SECURITY_PREF);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH &&
        screenSecurityPreference != null) {
      preferenceScreen.removePreference(screenSecurityPreference);
    }
  }

  private void initializeTimeoutSummary() {
    int timeoutMinutes = TextSecurePreferences.getPassphraseTimeoutInterval(getActivity());
    this.findPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF)
        .setSummary(getString(R.string.AppProtectionPreferenceFragment_minutes, timeoutMinutes));
  }

  private class ChangePassphraseClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      if (MasterSecretUtil.isPassphraseInitialized(getActivity())) {
        startActivity(new Intent(getActivity(), PassphraseChangeActivity.class));
      } else {
        Toast.makeText(getActivity(),
          R.string.ApplicationPreferenceActivity_you_havent_set_a_passphrase_yet,
          Toast.LENGTH_LONG).show();
      }

      return true;
    }
  }

  private class PassphraseIntervalClickListener implements Preference.OnPreferenceClickListener, HmsPickerDialogFragment.HmsPickerDialogHandler {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      HmsPickerBuilder hmsPicker = new HmsPickerBuilder().setFragmentManager(getFragmentManager())
                                                         .addHmsPickerDialogHandler(this);

      if (TextSecurePreferences.getTheme(getActivity()).equals("light")) {
        hmsPicker.setStyleResId(R.style.BetterPickersDialogFragment_Light);
      } else {
        hmsPicker.setStyleResId(R.style.BetterPickersDialogFragment);
      }

      hmsPicker.show();
      return true;
    }

    @Override
    public void onDialogHmsSet(int reference, int hours, int minutes, int seconds) {
      int timeoutMinutes = Math.max((int)TimeUnit.HOURS.toMinutes(hours) +
                                    minutes                         +
                                    (int)TimeUnit.SECONDS.toMinutes(seconds), 1);

      TextSecurePreferences.setPassphraseTimeoutInterval(getActivity(), timeoutMinutes);
      initializeTimeoutSummary();
    }
  }

  private class DisablePassphraseClickListener implements Preference.OnPreferenceChangeListener {

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {
      if (((CheckBoxPreference)preference).isChecked()) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.ApplicationPreferencesActivity_disable_storage_encryption);
        builder.setMessage(R.string.ApplicationPreferencesActivity_warning_this_will_disable_storage_encryption_for_all_messages);
        builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_alert_icon));
        builder.setPositiveButton(R.string.ApplicationPreferencesActivity_disable, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            MasterSecret masterSecret = getActivity().getIntent().getParcelableExtra("master_secret");
            MasterSecretUtil.changeMasterSecretPassphrase(getActivity(),
                                                          masterSecret,
                                                          MasterSecretUtil.UNENCRYPTED_PASSPHRASE);


            TextSecurePreferences.setPasswordDisabled(getActivity(), true);
            ((CheckBoxPreference)preference).setChecked(false);

            Intent intent = new Intent(getActivity(), KeyCachingService.class);
            intent.setAction(KeyCachingService.DISABLE_ACTION);
            getActivity().startService(intent);
          }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
      } else {
        Intent intent = new Intent(getActivity(), PassphraseChangeActivity.class);
        startActivity(intent);
      }

      return false;
    }
  }

  public static CharSequence getSummary(Context context) {
    final int onCapsResId  = R.string.ApplicationPreferencesActivity_On;
    final int offCapsResId = R.string.ApplicationPreferencesActivity_Off;

    return context.getString(TextSecurePreferences.isPasswordDisabled(context) ? offCapsResId : onCapsResId);
  }
}
