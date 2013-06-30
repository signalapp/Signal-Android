/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.Toast;

import com.actionbarsherlock.view.MenuItem;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactIdentityManager;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.thoughtcrime.securesms.util.Trimmer;

/**
 * The Activity for application preference display and management.
 *
 * @author Moxie Marlinspike
 *
 */

public class ApplicationPreferencesActivity extends PassphraseRequiredSherlockPreferenceActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener
{

  private static final int   PICK_IDENTITY_CONTACT        = 1;

  public static final String RINGTONE_PREF                    = "pref_key_ringtone";
  public static final String VIBRATE_PREF                     = "pref_key_vibrate";
  public static final String NOTIFICATION_PREF                = "pref_key_enable_notifications";
  public static final String LED_COLOR_PREF                   = "pref_led_color";
  public static final String LED_BLINK_PREF                   = "pref_led_blink";
  public static final String LED_BLINK_PREF_CUSTOM            = "pref_led_blink_custom";
  public static final String IDENTITY_PREF                    = "pref_choose_identity";
  public static final String ALL_MMS_PERF                     = "pref_all_mms";
  public static final String PASSPHRASE_TIMEOUT_INTERVAL_PREF = "pref_timeout_interval";
  public static final String PASSPHRASE_TIMEOUT_PREF          = "pref_timeout_passphrase";
  public static final String AUTO_KEY_EXCHANGE_PREF           = "pref_auto_complete_key_exchange";
  public static final String THEME_PREF                       = "pref_theme";
  public static final String LANGUAGE_PREF                    = "pref_language";
  public static final String ENTER_SENDS_PREF                 = "pref_enter_sends";
  public static final String ENTER_PRESENT_PREF               = "pref_enter_key";

  private static final String DISPLAY_CATEGORY_PREF        = "pref_display_category";

  private static final String VIEW_MY_IDENTITY_PREF        = "pref_view_identity";
  private static final String MANAGE_IDENTITIES_PREF       = "pref_manage_identity";
  private static final String CHANGE_PASSPHRASE_PREF	     = "pref_change_passphrase";

  public static final String USE_LOCAL_MMS_APNS_PREF = "pref_use_local_apns";
  public static final String MMSC_HOST_PREF          = "pref_apn_mmsc_host";
  public static final String MMSC_PROXY_HOST_PREF    = "pref_apn_mms_proxy";
  public static final String MMSC_PROXY_PORT_PREF    = "pref_apn_mms_proxy_port";

  public static final String SMS_DELIVERY_REPORT_PREF = "pref_delivery_report_sms";
  public static final String MMS_DELIVERY_REPORT_PREF = "pref_delivery_report_mms";

  public static final String THREAD_TRIM_ENABLED = "pref_trim_threads";
  public static final String THREAD_TRIM_LENGTH  = "pref_trim_length";
  public static final String THREAD_TRIM_NOW     = "pref_trim_now";

  public static final String LOCAL_NUMBER_PREF    = "pref_local_number";
  public static final String VERIFYING_STATE_PREF = "pref_verifying";
  public static final String REGISTERED_GCM_PREF  = "pref_gcm_registered";
  public static final String GCM_PASSWORD_PREF    = "pref_gcm_password";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(icicle);

    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    addPreferencesFromResource(R.xml.preferences);

    initializeIdentitySelection();
    initializeEditTextSummaries();

    this.findPreference(VIEW_MY_IDENTITY_PREF)
      .setOnPreferenceClickListener(new ViewMyIdentityClickListener());
    this.findPreference(MANAGE_IDENTITIES_PREF)
      .setOnPreferenceClickListener(new ManageIdentitiesClickListener());
    this.findPreference(CHANGE_PASSPHRASE_PREF)
      .setOnPreferenceClickListener(new ChangePassphraseClickListener());
    this.findPreference(THREAD_TRIM_NOW)
      .setOnPreferenceClickListener(new TrimNowClickListener());
    this.findPreference(THREAD_TRIM_LENGTH)
      .setOnPreferenceChangeListener(new TrimLengthValidationListener());
  }

  @Override
  public void onStart() {
    super.onStart();
    getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onStop() {
    super.onStop();
    getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onDestroy() {
    MemoryCleaner.clean((MasterSecret) getIntent().getParcelableExtra("master_secret"));
    super.onDestroy();
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, Intent data) {
    super.onActivityResult(reqCode, resultCode, data);

    if (resultCode == Activity.RESULT_OK) {
      switch (reqCode) {
      case PICK_IDENTITY_CONTACT: handleIdentitySelection(data); break;
      }
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case android.R.id.home:
      Intent intent = new Intent(this, ConversationListActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(intent);
      finish();
      return true;
    }

    return false;
  }

  private void initializeEditTextSummary(final EditTextPreference preference) {
    if (preference.getText() == null) {
      preference.setSummary("Not set");
    } else {
      preference.setSummary(preference.getText());
    }

    preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference pref, Object newValue) {
        preference.setSummary(newValue == null ? "Not set" : ((String) newValue));
        return true;
      }
    });
  }

  private void initializeEditTextSummaries() {
    initializeEditTextSummary((EditTextPreference)this.findPreference(MMSC_HOST_PREF));
    initializeEditTextSummary((EditTextPreference)this.findPreference(MMSC_PROXY_HOST_PREF));
    initializeEditTextSummary((EditTextPreference)this.findPreference(MMSC_PROXY_PORT_PREF));
  }

  private void initializeIdentitySelection() {
    ContactIdentityManager identity = ContactIdentityManager.getInstance(this);

    if (identity.isSelfIdentityAutoDetected()) {
      Preference preference = this.findPreference(DISPLAY_CATEGORY_PREF);
      this.getPreferenceScreen().removePreference(preference);
    } else {
      Uri contactUri = identity.getSelfIdentityUri();

      if (contactUri != null) {
        String contactName = ContactAccessor.getInstance().getNameFromContact(this, contactUri);
        this.findPreference(IDENTITY_PREF)
          .setSummary(String.format(getString(R.string.ApplicationPreferencesActivity_currently_s),
                      contactName));
      }

      this.findPreference(IDENTITY_PREF)
        .setOnPreferenceClickListener(new IdentityPreferenceClickListener());
    }
  }

  private void handleIdentitySelection(Intent data) {
    Uri contactUri = data.getData();

    if (contactUri != null) {
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
      String contactUriString       = contactUri.toString();

      preferences.edit().putString(IDENTITY_PREF, contactUriString).commit();

      initializeIdentitySelection();
    }
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key.equals(THEME_PREF)) {
      dynamicTheme.onResume(this);
    } else if (key.equals(LANGUAGE_PREF)) {
      dynamicLanguage.onResume(this);
    }
  }

  private class IdentityPreferenceClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(Intent.ACTION_PICK);
      intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
      startActivityForResult(intent, PICK_IDENTITY_CONTACT);
      return true;
    }
  }

  private class ViewMyIdentityClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent viewIdentityIntent = new Intent(ApplicationPreferencesActivity.this, ViewIdentityActivity.class);
      viewIdentityIntent.putExtra("identity_key", IdentityKeyUtil.getIdentityKey(ApplicationPreferencesActivity.this));
      viewIdentityIntent.putExtra("title", getString(R.string.ApplicationPreferencesActivity_my) + " " +
                                           getString(R.string.ViewIdentityActivity_identity_fingerprint));
      startActivity(viewIdentityIntent);

      return true;
    }
  }

  private class ManageIdentitiesClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      MasterSecret masterSecret = (MasterSecret)getIntent().getParcelableExtra("master_secret");

      if (masterSecret != null) {
        Intent manageIntent = new Intent(ApplicationPreferencesActivity.this, ReviewIdentitiesActivity.class);
        manageIntent.putExtra("master_secret", masterSecret);
        startActivity(manageIntent);
      } else {
        Toast.makeText(ApplicationPreferencesActivity.this,
                       R.string.ApplicationPreferenceActivity_you_need_to_have_entered_your_passphrase_before_managing_keys,
                       Toast.LENGTH_LONG).show();
      }

      return true;
    }
  }

  private class ChangePassphraseClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
       if (MasterSecretUtil.isPassphraseInitialized(ApplicationPreferencesActivity.this)) {
        startActivity(new Intent(ApplicationPreferencesActivity.this, PassphraseChangeActivity.class));
      } else {
        Toast.makeText(ApplicationPreferencesActivity.this,
                       R.string.ApplicationPreferenceActivity_you_havent_set_a_passphrase_yet,
                       Toast.LENGTH_LONG).show();
      }

      return true;
    }
  }

  private class TrimNowClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final int threadLengthLimit = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ApplicationPreferencesActivity.this)
                                                                      .getString(THREAD_TRIM_LENGTH, "500"));

      AlertDialog.Builder builder = new AlertDialog.Builder(ApplicationPreferencesActivity.this);
      builder.setTitle(R.string.ApplicationPreferencesActivity_delete_all_old_messages_now);
      builder.setMessage(String.format(getString(R.string.ApplicationPreferencesActivity_are_you_sure_you_would_like_to_immediately_trim_all_conversation_threads_to_the_s_most_recent_messages),
      		                             threadLengthLimit));
      builder.setPositiveButton(R.string.ApplicationPreferencesActivity_delete,
                                new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          Trimmer.trimAllThreads(ApplicationPreferencesActivity.this, threadLengthLimit);
        }
      });

      builder.setNegativeButton(android.R.string.cancel, null);
      builder.show();

      return true;
    }
  }

  private class TrimLengthValidationListener implements Preference.OnPreferenceChangeListener {

    public TrimLengthValidationListener() {
      EditTextPreference preference = (EditTextPreference)findPreference(THREAD_TRIM_LENGTH);
      preference.setSummary(preference.getText() + " " + getString(R.string.ApplicationPreferencesActivity_messages_per_conversation));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      if (newValue == null || ((String)newValue).trim().length() == 0) {
        return false;
      }

      try {
        Integer.parseInt((String)newValue);
      } catch (NumberFormatException nfe) {
        Log.w("ApplicationPreferencesActivity", nfe);
        return false;
      }

      if (Integer.parseInt((String)newValue) < 1) {
        return false;
      }

      preference.setSummary((String)newValue + " " +
                            getString(R.string.ApplicationPreferencesActivity_messages_per_conversation));
      return true;
    }

  }

  /* http://code.google.com/p/android/issues/detail?id=4611#c35 */
  @SuppressWarnings("deprecation")
  @Override
  public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference)
  {
    super.onPreferenceTreeClick(preferenceScreen, preference);
    if (preference!=null)
      if (preference instanceof PreferenceScreen)
          if (((PreferenceScreen)preference).getDialog()!=null)
            ((PreferenceScreen)preference).getDialog().getWindow().getDecorView().setBackgroundDrawable(this.getWindow().getDecorView().getBackground().getConstantState().newDrawable());
    return false;
  }

}
