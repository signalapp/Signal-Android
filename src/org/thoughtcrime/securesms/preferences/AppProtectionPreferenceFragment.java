package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v4.preference.PreferenceFragment;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import com.doomonafireball.betterpickers.hmspicker.HmsPickerBuilder;
import com.doomonafireball.betterpickers.hmspicker.HmsPickerDialogFragment;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.BlockedContactsActivity;
import org.thoughtcrime.securesms.PassphraseChangeActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class AppProtectionPreferenceFragment extends PreferenceFragment {

  private static final String PREFERENCE_CATEGORY_BLOCKED = "preference_category_blocked";

  private MasterSecret       masterSecret;
  private CheckBoxPreference disablePassphrase;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_app_protection);

    masterSecret      = getArguments().getParcelable("master_secret");
    disablePassphrase = (CheckBoxPreference) this.findPreference("pref_enable_passphrase_temporary");

    this.findPreference(TextSecurePreferences.CHANGE_PASSPHRASE_PREF)
        .setOnPreferenceClickListener(new ChangePassphraseClickListener());
    this.findPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF)
        .setOnPreferenceClickListener(new PassphraseIntervalClickListener());
    this.findPreference(PREFERENCE_CATEGORY_BLOCKED)
        .setOnPreferenceClickListener(new BlockedContactsClickListener());
    disablePassphrase
        .setOnPreferenceChangeListener(new DisablePassphraseClickListener());
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__privacy);

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

  private class BlockedContactsClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(getActivity(), BlockedContactsActivity.class);
      startActivity(intent);
      return true;
    }
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
      int[]      attributes = {R.attr.app_protect_timeout_picker_color};
      TypedArray hmsStyle   = getActivity().obtainStyledAttributes(attributes);

      new HmsPickerBuilder().setFragmentManager(getFragmentManager())
                            .setStyleResId(hmsStyle.getResourceId(0, R.style.BetterPickersDialogFragment_Light))
                            .addHmsPickerDialogHandler(this)
                            .show();

      hmsStyle.recycle();

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
        builder.setTitle(R.string.ApplicationPreferencesActivity_disable_passphrase);
        builder.setMessage(R.string.ApplicationPreferencesActivity_disable_lock_screen);
        builder.setIconAttribute(R.attr.dialog_alert_icon);
        builder.setPositiveButton(R.string.ApplicationPreferencesActivity_disable, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
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

  private static CharSequence getPassphraseSummary(Context context) {
    final int    passphraseResId = R.string.preferences__passphrase_summary;
    final String onRes           = context.getString(R.string.ApplicationPreferencesActivity_on);
    final String offRes          = context.getString(R.string.ApplicationPreferencesActivity_off);

    if (TextSecurePreferences.isPasswordDisabled(context)) {
      return context.getString(passphraseResId, offRes);
    } else {
      return context.getString(passphraseResId, onRes);
    }
  }

  private static CharSequence getScreenSecuritySummary(Context context) {
    final int    screenSecurityResId = R.string.preferences__screen_security_summary;
    final String onRes               = context.getString(R.string.ApplicationPreferencesActivity_on);
    final String offRes              = context.getString(R.string.ApplicationPreferencesActivity_off);

    if (TextSecurePreferences.isScreenSecurityEnabled(context)) {
      return context.getString(screenSecurityResId, onRes);
    } else {
      return context.getString(screenSecurityResId, offRes);
    }
  }

  public static CharSequence getSummary(Context context) {
    return getPassphraseSummary(context) + ", " + getScreenSecuritySummary(context);
  }
}
