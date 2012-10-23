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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactIdentityManager;
import org.thoughtcrime.securesms.crypto.IdentityKey;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import java.util.List;

/**
 * The Activity for application preference display and management.
 *
 * @author Moxie Marlinspike
 *
 */

public class ApplicationPreferencesActivity extends SherlockPreferenceActivity {

  private static final int   PICK_IDENTITY_CONTACT        = 1;
  private static final int   IMPORT_IDENTITY_ID           = 2;

  public static final String WHITESPACE_PREF                  = "pref_key_tag_whitespace";
  public static final String RINGTONE_PREF                    = "pref_key_ringtone";
  public static final String VIBRATE_PREF                     = "pref_key_vibrate";
  public static final String NOTIFICATION_PREF                = "pref_key_enable_notifications";
  public static final String LED_COLOR_PREF                   = "pref_led_color";
  public static final String LED_BLINK_PREF                   = "pref_led_blink";
  public static final String LED_BLINK_PREF_CUSTOM            = "pref_led_blink_custom";
  public static final String IDENTITY_PREF                    = "pref_choose_identity";
  public static final String SEND_IDENTITY_PREF               = "pref_send_identity_key";
  public static final String ALL_MMS_PERF                     = "pref_all_mms";
  public static final String PASSPHRASE_TIMEOUT_INTERVAL_PREF = "pref_timeout_interval";
  public static final String PASSPHRASE_TIMEOUT_PREF          = "pref_timeout_passphrase";
  public static final String AUTO_KEY_EXCHANGE_PREF           = "pref_auto_complete_key_exchange";

  private static final String DISPLAY_CATEGORY_PREF        = "pref_display_category";

  private static final String VIEW_MY_IDENTITY_PREF        = "pref_view_identity";
  private static final String EXPORT_MY_IDENTITY_PREF      = "pref_export_identity";
  private static final String IMPORT_CONTACT_IDENTITY_PREF = "pref_import_identity";
  private static final String MANAGE_IDENTITIES_PREF       = "pref_manage_identity";
  private static final String CHANGE_PASSPHRASE_PREF	     = "pref_change_passphrase";

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    addPreferencesFromResource(R.xml.preferences);

    initializeIdentitySelection();

    this.findPreference(VIEW_MY_IDENTITY_PREF)
      .setOnPreferenceClickListener(new ViewMyIdentityClickListener());
    this.findPreference(EXPORT_MY_IDENTITY_PREF)
      .setOnPreferenceClickListener(new ExportMyIdentityClickListener());
    this.findPreference(IMPORT_CONTACT_IDENTITY_PREF)
      .setOnPreferenceClickListener(new ImportContactIdentityClickListener());
    this.findPreference(MANAGE_IDENTITIES_PREF)
      .setOnPreferenceClickListener(new ManageIdentitiesClickListener());
    this.findPreference(CHANGE_PASSPHRASE_PREF)
      .setOnPreferenceClickListener(new ChangePassphraseClickListener());
  }

  @Override
  public void onStart() {
    super.onStart();
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_START_EVENT);
    startService(intent);
  }

  @Override
  public void onStop() {
    super.onStop();
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_STOP_EVENT);
    startService(intent);
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, Intent data) {
    super.onActivityResult(reqCode, resultCode, data);

    if (resultCode == Activity.RESULT_OK) {
      switch (reqCode) {
      case (PICK_IDENTITY_CONTACT) : handleIdentitySelection(data); break;
      case IMPORT_IDENTITY_ID:       importIdentityKey(data.getData()); break;
      }
    }
  }

  @Override
  public void onDestroy() {
    MemoryCleaner.clean((MasterSecret)getIntent().getParcelableExtra("master_secret"));
    super.onDestroy();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case android.R.id.home: finish(); return true;
    }

    return false;
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

  private void importIdentityKey(Uri uri) {
    IdentityKey identityKey = ContactAccessor.getInstance().importIdentityKey(this, uri);
    String contactName      = ContactAccessor.getInstance().getNameFromContact(this, uri);

    if (identityKey == null) {
      Dialogs.displayAlert(this,
                           getString(R.string.ApplicationPreferenceActivity_not_found_exclamation),
                           getString(R.string.ApplicationPreferenceActivity_no_valid_identity_key_was_found_in_the_specified_contact),
                           android.R.drawable.ic_dialog_alert);
      return;
    }

    Intent verifyImportedKeyIntent = new Intent(this, VerifyImportedIdentityActivity.class);
    verifyImportedKeyIntent.putExtra("master_secret", getIntent().getParcelableExtra("master_secret"));
    verifyImportedKeyIntent.putExtra("identity_key", identityKey);
    verifyImportedKeyIntent.putExtra("contact_name", contactName);
    startActivity(verifyImportedKeyIntent);
  }

  private class IdentityPreferenceClickListener implements Preference.OnPreferenceClickListener {
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(Intent.ACTION_PICK);
      intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
      startActivityForResult(intent, PICK_IDENTITY_CONTACT);
      return true;
    }
  }

  private class ViewMyIdentityClickListener implements Preference.OnPreferenceClickListener {
    public boolean onPreferenceClick(Preference preference) {
      Intent viewIdentityIntent = new Intent(ApplicationPreferencesActivity.this, ViewIdentityActivity.class);
      viewIdentityIntent.putExtra("identity_key", IdentityKeyUtil.getIdentityKey(ApplicationPreferencesActivity.this));
      startActivity(viewIdentityIntent);

      return true;
    }
  }

  private class ExportMyIdentityClickListener implements Preference.OnPreferenceClickListener {
    public boolean onPreferenceClick(Preference preference) {
      if (!IdentityKeyUtil.hasIdentityKey(ApplicationPreferencesActivity.this)) {
        Toast.makeText(ApplicationPreferencesActivity.this,
                       R.string.ApplicationPreferenceActivity_you_don_t_have_an_identity_key_exclamation,
                       Toast.LENGTH_LONG).show();
        return true;
      }

      List<Long> rawContactIds = ContactIdentityManager
                                   .getInstance(ApplicationPreferencesActivity.this)
                                   .getSelfIdentityRawContactIds();

      if (rawContactIds== null) {
          Toast.makeText(ApplicationPreferencesActivity.this,
                         R.string.ApplicationPreferenceActivity_you_have_not_yet_defined_a_contact_for_yourself,
                         Toast.LENGTH_LONG).show();
          return true;
      }

      ContactAccessor.getInstance().insertIdentityKey(ApplicationPreferencesActivity.this, rawContactIds,
                                                      IdentityKeyUtil.getIdentityKey(ApplicationPreferencesActivity.this));

      Toast.makeText(ApplicationPreferencesActivity.this,
                     R.string.ApplicationPreferenceActivity_exported_to_contacts_database,
                     Toast.LENGTH_LONG).show();

      return true;
    }
  }

  private class ImportContactIdentityClickListener implements Preference.OnPreferenceClickListener {
    public boolean onPreferenceClick(Preference preference) {
      MasterSecret masterSecret = (MasterSecret)getIntent().getParcelableExtra("master_secret");

      if (masterSecret != null) {
        Intent importIntent = new Intent(Intent.ACTION_PICK);
        importIntent.setType(ContactsContract.Contacts.CONTENT_TYPE);
        startActivityForResult(importIntent, IMPORT_IDENTITY_ID);
      } else {
        Toast.makeText(ApplicationPreferencesActivity.this,
                       R.string.ApplicationPreferenceActivity_you_need_to_have_entered_your_passphrase_before_importing_keys,
                       Toast.LENGTH_LONG).show();
      }

      return true;
    }
  }

  private class ManageIdentitiesClickListener implements Preference.OnPreferenceClickListener {
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
    public boolean onPreferenceClick(Preference preference) {
      SharedPreferences settings = getSharedPreferences(KeyCachingService.PREFERENCES_NAME, 0);

      if (settings.getBoolean("passphrase_initialized", false)) {
        startActivity(new Intent(ApplicationPreferencesActivity.this, PassphraseChangeActivity.class));
      } else {
        Toast.makeText(ApplicationPreferencesActivity.this,
                       R.string.ApplicationPreferenceActivity_you_havent_set_a_passphrase_yet,
                       Toast.LENGTH_LONG).show();
      }

      return true;
    }
  }

}
