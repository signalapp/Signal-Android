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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.actionbarsherlock.view.MenuItem;
import com.google.android.gcm.GCMRegistrar;

import org.thoughtcrime.securesms.components.OutgoingSmsPreference;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactIdentityManager;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Trimmer;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.push.AuthorizationFailedException;
import org.whispersystems.textsecure.push.PushServiceSocket;

import java.io.IOException;

/**
 * The Activity for application preference display and management.
 *
 * @author Moxie Marlinspike
 *
 */

public class ApplicationPreferencesActivity extends PassphraseRequiredSherlockPreferenceActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener
{
  private static final String TAG = "Preferences";

  private static final int PICK_IDENTITY_CONTACT        = 1;
  private static final int ENABLE_PASSPHRASE_ACTIVITY   = 2;

  private static final String DISPLAY_CATEGORY_PREF = "pref_display_category";
  private static final String PUSH_MESSAGING_PREF   = "pref_toggle_push_messaging";
  private static final String MMS_PREF              = "pref_mms_preferences";
  private static final String KITKAT_DEFAULT_PREF   = "pref_set_default";
  private static final String UPDATE_DIRECTORY_PREF = "pref_update_directory";
  private static final String SUBMIT_DEBUG_LOG_PREF = "pref_submit_debug_logs";
  private static final String OUTGOING_SMS_PREF     = "pref_outgoing_sms";

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
    initializePushMessagingToggle();

    this.findPreference(TextSecurePreferences.CHANGE_PASSPHRASE_PREF)
      .setOnPreferenceClickListener(new ChangePassphraseClickListener());
    this.findPreference(TextSecurePreferences.THREAD_TRIM_NOW)
      .setOnPreferenceClickListener(new TrimNowClickListener());
    this.findPreference(TextSecurePreferences.THREAD_TRIM_LENGTH)
      .setOnPreferenceChangeListener(new TrimLengthValidationListener());
    this.findPreference(TextSecurePreferences.DISABLE_PASSPHRASE_PREF)
      .setOnPreferenceChangeListener(new DisablePassphraseClickListener());
    this.findPreference(MMS_PREF)
      .setOnPreferenceClickListener(new ApnPreferencesClickListener());
    this.findPreference(TextSecurePreferences.LED_COLOR_PREF)
      .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(TextSecurePreferences.LED_BLINK_PREF)
      .setOnPreferenceChangeListener(new ListSummaryListener());
    this.findPreference(TextSecurePreferences.RINGTONE_PREF)
      .setOnPreferenceChangeListener(new RingtoneSummaryListener());
    this.findPreference(UPDATE_DIRECTORY_PREF)
        .setOnPreferenceClickListener(new DirectoryUpdateListener());
    this.findPreference(SUBMIT_DEBUG_LOG_PREF)
        .setOnPreferenceClickListener(new SubmitDebugLogListener());
    this.findPreference(OUTGOING_SMS_PREF)
        .setOnPreferenceChangeListener(new OutgoingSmsPreferenceListener());

    initializeOutgoingSmsSummary((OutgoingSmsPreference) findPreference(OUTGOING_SMS_PREF));
    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.LED_COLOR_PREF));
    initializeListSummary((ListPreference) findPreference(TextSecurePreferences.LED_BLINK_PREF));
    initializeRingtoneSummary((RingtonePreference) findPreference(TextSecurePreferences.RINGTONE_PREF));
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

    initializePlatformSpecificOptions();
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

    Log.w("ApplicationPreferencesActivity", "Got result: " + resultCode + " for req: " + reqCode);

    if (resultCode == Activity.RESULT_OK) {
      switch (reqCode) {
      case PICK_IDENTITY_CONTACT:      handleIdentitySelection(data); break;
      case ENABLE_PASSPHRASE_ACTIVITY: finish();                      break;
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

  private void initializePlatformSpecificOptions() {
    PreferenceGroup    pushSmsCategory          = (PreferenceGroup) findPreference("push_sms_category");
    PreferenceGroup    advancedCategory         = (PreferenceGroup) findPreference("advanced_category");
    Preference         defaultPreference        = findPreference(KITKAT_DEFAULT_PREF);
    Preference         allSmsPreference         = findPreference(TextSecurePreferences.ALL_SMS_PREF);
    Preference         allMmsPreference         = findPreference(TextSecurePreferences.ALL_MMS_PREF);
    Preference         screenSecurityPreference = findPreference(TextSecurePreferences.SCREEN_SECURITY_PREF);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && pushSmsCategory != null) {
      if (allSmsPreference != null) pushSmsCategory.removePreference(allSmsPreference);
      if (allMmsPreference != null) pushSmsCategory.removePreference(allMmsPreference);

      if (Util.isDefaultSmsProvider(this)) {
        defaultPreference.setIntent(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_sms_enabled));
        defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_touch_to_change_your_default_sms_app));
      } else {
        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
        defaultPreference.setIntent(intent);
        defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_sms_disabled));
        defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_touch_to_make_textsecure_your_default_sms_app));
      }
    } else if (pushSmsCategory != null && defaultPreference != null) {
      pushSmsCategory.removePreference(defaultPreference);
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 &&
        advancedCategory != null                                   &&
        screenSecurityPreference != null)
    {
      advancedCategory.removePreference(screenSecurityPreference);
    }
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

  private void initializePushMessagingToggle() {
    CheckBoxPreference preference = (CheckBoxPreference)this.findPreference(PUSH_MESSAGING_PREF);
    preference.setChecked(TextSecurePreferences.isPushRegistered(this));
    preference.setOnPreferenceChangeListener(new PushMessagingClickListener());
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
        this.findPreference(TextSecurePreferences.IDENTITY_PREF)
          .setSummary(String.format(getString(R.string.ApplicationPreferencesActivity_currently_s),
                      contactName));
      }

      this.findPreference(TextSecurePreferences.IDENTITY_PREF)
        .setOnPreferenceClickListener(new IdentityPreferenceClickListener());
    }
  }

  private void initializeListSummary(ListPreference pref) {
    pref.setSummary(pref.getEntry());
  }

  private void initializeRingtoneSummary(RingtonePreference pref) {
    RingtoneSummaryListener listener =
      (RingtoneSummaryListener) pref.getOnPreferenceChangeListener();
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    listener.onPreferenceChange(pref, sharedPreferences.getString(pref.getKey(), ""));
  }

  private void initializeOutgoingSmsSummary(OutgoingSmsPreference pref) {
    pref.setSummary(buildOutgoingSmsDescription());
  }

  private void handleIdentitySelection(Intent data) {
    Uri contactUri = data.getData();

    if (contactUri != null) {
      TextSecurePreferences.setIdentityContactUri(this, contactUri.toString());
      initializeIdentitySelection();
    }
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key.equals(TextSecurePreferences.THEME_PREF)) {
      dynamicTheme.onResume(this);
    } else if (key.equals(TextSecurePreferences.LANGUAGE_PREF)) {
      dynamicLanguage.onResume(this);
    }
  }

  private class PushMessagingClickListener implements Preference.OnPreferenceChangeListener {

    private static final int SUCCESS       = 0;
    private static final int NETWORK_ERROR = 1;

    private class DisablePushMessagesTask extends AsyncTask<Void, Void, Integer> {
      private ProgressDialog dialog;
      private final Preference preference;

      public DisablePushMessagesTask(final Preference preference) {
        this.preference = preference;
      }

      @Override
      protected void onPreExecute() {
        dialog = ProgressDialog.show(ApplicationPreferencesActivity.this,
                                     getString(R.string.ApplicationPreferencesActivity_unregistering),
                                     getString(R.string.ApplicationPreferencesActivity_unregistering_for_data_based_communication),
                                     true, false);
      }

      @Override
      protected void onPostExecute(Integer result) {
        if (dialog != null)
          dialog.dismiss();

        switch (result) {
          case NETWORK_ERROR:
            Toast.makeText(ApplicationPreferencesActivity.this,
                           getString(R.string.ApplicationPreferencesActivity_error_connecting_to_server),
                           Toast.LENGTH_LONG).show();
            break;
          case SUCCESS:
            ((CheckBoxPreference)preference).setChecked(false);
            TextSecurePreferences.setPushRegistered(ApplicationPreferencesActivity.this, false);
            break;
        }
      }

      @Override
      protected Integer doInBackground(Void... params) {
        try {
          Context           context = ApplicationPreferencesActivity.this;
          PushServiceSocket socket  = PushServiceSocketFactory.create(context);

          socket.unregisterGcmId();
          GCMRegistrar.unregister(context);
          return SUCCESS;
        } catch (AuthorizationFailedException afe) {
          Log.w("ApplicationPreferencesActivity", afe);
          return SUCCESS;
        } catch (IOException ioe) {
          Log.w("ApplicationPreferencesActivity", ioe);
          return NETWORK_ERROR;
        }
      }
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {
      if (((CheckBoxPreference)preference).isChecked()) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ApplicationPreferencesActivity.this);
        builder.setIcon(Dialogs.resolveIcon(ApplicationPreferencesActivity.this, R.attr.dialog_info_icon));
        builder.setTitle(getString(R.string.ApplicationPreferencesActivity_disable_push_messages));
        builder.setMessage(getString(R.string.ApplicationPreferencesActivity_this_will_disable_push_messages));
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            new DisablePushMessagesTask(preference).execute();
          }
        });
        builder.show();
      } else {
        Intent intent = new Intent(ApplicationPreferencesActivity.this, RegistrationActivity.class);
        intent.putExtra("master_secret", getIntent().getParcelableExtra("master_secret"));
        startActivity(intent);
      }

      return false;
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
      final int threadLengthLimit = TextSecurePreferences.getThreadTrimLength(ApplicationPreferencesActivity.this);
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

  private class DisablePassphraseClickListener implements Preference.OnPreferenceChangeListener {

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {
      if (!((CheckBoxPreference)preference).isChecked()) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ApplicationPreferencesActivity.this);
        builder.setTitle(R.string.ApplicationPreferencesActivity_disable_storage_encryption);
        builder.setMessage(R.string.ApplicationPreferencesActivity_warning_this_will_disable_storage_encryption_for_all_messages);
        builder.setIcon(Dialogs.resolveIcon(ApplicationPreferencesActivity.this, R.attr.dialog_alert_icon));
        builder.setPositiveButton(R.string.ApplicationPreferencesActivity_disable, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            MasterSecret masterSecret = getIntent().getParcelableExtra("master_secret");
            MasterSecretUtil.changeMasterSecretPassphrase(ApplicationPreferencesActivity.this,
                                                          masterSecret,
                                                          MasterSecretUtil.UNENCRYPTED_PASSPHRASE);


            TextSecurePreferences.setPasswordDisabled(ApplicationPreferencesActivity.this, true);
            ((CheckBoxPreference)preference).setChecked(true);

            Intent intent = new Intent(ApplicationPreferencesActivity.this, KeyCachingService.class);
            intent.setAction(KeyCachingService.DISABLE_ACTION);
            startService(intent);
          }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
      } else {
        Intent intent = new Intent(ApplicationPreferencesActivity.this,
                                   PassphraseChangeActivity.class);
        startActivityForResult(intent, ENABLE_PASSPHRASE_ACTIVITY);
      }

      return false;
    }
  }

  private class TrimLengthValidationListener implements Preference.OnPreferenceChangeListener {

    public TrimLengthValidationListener() {
      EditTextPreference preference = (EditTextPreference)findPreference(TextSecurePreferences.THREAD_TRIM_LENGTH);
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

      preference.setSummary(newValue + " " +
                            getString(R.string.ApplicationPreferencesActivity_messages_per_conversation));
      return true;
    }

  }

  private class ApnPreferencesClickListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      startActivity(new Intent(ApplicationPreferencesActivity.this, MmsPreferencesActivity.class));
      return true;
    }
  }

  private class ListSummaryListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
      ListPreference asList = (ListPreference) preference;

      int index = 0;
      for (; index < asList.getEntryValues().length; index++) {
        if (value.equals(asList.getEntryValues()[index])) {
          break;
        }
      }

      asList.setSummary(asList.getEntries()[index]);
      return true;
    }
  }

  private class RingtoneSummaryListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      String value = (String) newValue;

      if (TextUtils.isEmpty(value)) {
        preference.setSummary(R.string.preferences__default);
      } else {
        Ringtone tone = RingtoneManager.getRingtone(ApplicationPreferencesActivity.this,
          Uri.parse(value));
        if (tone != null) {
          preference.setSummary(tone.getTitle(ApplicationPreferencesActivity.this));
        }
      }

      return true;
    }
  }

  private class DirectoryUpdateListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      DirectoryHelper.refreshDirectoryWithProgressDialog(ApplicationPreferencesActivity.this);
      return true;
    }
  }

  private class SubmitDebugLogListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final Intent intent = new Intent(ApplicationPreferencesActivity.this, LogSubmitActivity.class);
      startActivity(intent);
      return true;
    }
  }

  private class OutgoingSmsPreferenceListener implements Preference.OnPreferenceChangeListener {

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {

      preference.setSummary(buildOutgoingSmsDescription());
      return false;
    }
  }

  private String buildOutgoingSmsDescription() {
    final StringBuilder builder             = new StringBuilder();
    final boolean       dataFallback        = TextSecurePreferences.isSmsFallbackEnabled(this);
    final boolean       dataFallbackAskSms  = TextSecurePreferences.isSmsFallbackAskEnabled(this);
    final boolean       dataFallbackAskMms  = TextSecurePreferences.isMmsFallbackAskEnabled(this);
    final boolean       nonData             = TextSecurePreferences.isSmsNonDataOutEnabled(this);
    final boolean       mmsDisabled          = TextSecurePreferences.isMmsCompletelyDisabled(this);

    if (dataFallback) {
      builder.append(getString(R.string.preferences__sms_outgoing_push_users));

      if (dataFallbackAskSms || (dataFallbackAskMms && !mmsDisabled)) {
        builder.append(" (");

        if (dataFallbackAskSms)
          builder.append(getString(R.string.preferences__sms_fallback_push_users_ask));

        if (dataFallbackAskMms && !mmsDisabled) {
          if (dataFallbackAskSms) builder.append(", ");
          builder.append(getString(R.string.preferences__mms_fallback_push_users_ask));
        }

        builder.append(")");
      }
    }
    if (nonData) {
      if (dataFallback) builder.append(", ");
      builder.append(getString(R.string.preferences__sms_fallback_non_push_users));
    }
    if( mmsDisabled && (dataFallback || nonData))
      builder.append(", ").append(getString(R.string.preferences__mms_disabled));
    if (!dataFallback && !nonData) {
      builder.append(getString(R.string.preferences__sms_fallback_nobody));
    }
    return builder.toString();
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
