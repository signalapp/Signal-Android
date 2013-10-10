package org.thoughtcrime.securesms;

import org.thoughtcrime.securesms.preferences.SoundPreference;
import org.thoughtcrime.securesms.preferences.VibratePatternListPreference;
import org.thoughtcrime.securesms.preferences.LedBlinkPatternListPreference;
import org.thoughtcrime.securesms.preferences.TestNotificationDialogPreference;
import org.thoughtcrime.securesms.providers.NotificationContract.ContactNotifications;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.NotificationsDatabase;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.provider.ContactsContract.Contacts;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class ConfigNotificationActivity extends PreferenceActivity {
  private final DynamicTheme    dynamicTheme    = new DynamicTheme();

  private long rowId;
  public static final String EXTRA_CONTACT_URI = "org.thoughtcrime.securesms.EXTRA_CONTACT_URI";

  public static final String PREF_ENABLED                = "c_pref_key_notification_enabled";
  public static final String PREF_TEST                   = "c_pref_key_test_notification";
  public static final String PREF_SOUND                  = "c_pref_key_sound";
  public static final String PREF_VIBRATE                = "c_pref_key_vibrate";
  public static final String PREF_VIBRATE_PATTERN        = "c_pref_key_vibrate_pattern";
  public static final String PREF_VIBRATE_PATTERN_CUSTOM = "c_pref_key_vibrate_pattern_custom";
  public static final String PREF_LED                    = "c_pref_key_led";
  public static final String PREF_LED_COLOR              = "c_pref_key_led_color";
  public static final String PREF_LED_PATTERN            = "c_pref_key_led_pattern";
  public static final String PREF_LED_PATTERN_CUSTOM     = "c_pref_key_led_pattern_custom";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    dynamicTheme.onCreate(this);

    super.onCreate(savedInstanceState);

    // Create and setup preferences
    createOrFetchContactPreferences();
  }

  @Override
  protected void onResume() {
    super.onResume();

    SoundPreference soundPref = (SoundPreference) findPreference(PREF_SOUND);
    final Uri ringtoneUri = soundPref.getSound();
    final Ringtone sound = RingtoneManager.getRingtone(this, ringtoneUri);

    if (sound == null || ringtoneUri.toString().isEmpty()) {
      soundPref.setSummary(getString(R.string.sound_silent));
    } else {
      soundPref.setSummary(sound.getTitle(this));
    }
  }

  private void createOrFetchContactPreferences() {
    // This Uri can be built by either NotificationsDatabase.buildContactUri() or
    // NotificationsDatabase.buildLookupUri().
    final Uri contactNotificationsUri = getIntent().getParcelableExtra(EXTRA_CONTACT_URI);

    Cursor c = getContentResolver().query(contactNotificationsUri, null, null, null, null);

    // notification not found, create new notification
    if (c == null || c.getCount() < 1) {
      c = createContact(contactNotificationsUri);
    }

    // Let Activity manage the cursor
    startManagingCursor(c);

    // Add preference layout from XML
    addPreferencesFromResource(R.xml.notification);

    initPreferences(c);
  }

  /*
   * All preferences will trigger this when changed
   */
  private OnPreferenceChangeListener onPrefChangeListener = new OnPreferenceChangeListener() {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      return storePreferences(preference, newValue);
    }
  };

  /*
   * Store a single preference back to the database
   */
  private boolean storePreferences(Preference preference, Object newValue) {
    String key = preference.getKey();
    String column = null;

    if (key.equals(PREF_ENABLED)) {
      column = NotificationsDatabase.ENABLED;
    } else if (key.equals(PREF_SOUND)) {
      column = NotificationsDatabase.SOUND;
    } else if (key.equals(PREF_VIBRATE)) {
      column = NotificationsDatabase.VIBRATE;
    } else if (key.equals(PREF_VIBRATE_PATTERN)) {
      column = NotificationsDatabase.VIBRATE_PATTERN;
    } else if (key.equals(PREF_VIBRATE_PATTERN_CUSTOM)) {
      column = NotificationsDatabase.VIBRATE_PATTERN_CUSTOM;
    } else if (key.equals(PREF_LED)) {
      column = NotificationsDatabase.LED;
    } else if (key.equals(PREF_LED_COLOR)) {
      column = NotificationsDatabase.LED_COLOR;
    } else if (key.equals(PREF_LED_PATTERN)) {
      column = NotificationsDatabase.LED_PATTERN;
    } else if (key.equals(PREF_LED_PATTERN_CUSTOM)) {
      column = NotificationsDatabase.LED_PATTERN_CUSTOM;
    } else {
      return false;
    }

    ContentValues vals = new ContentValues();
    if (newValue.getClass().equals(Boolean.class)) {
      vals.put(column, (Boolean) newValue);
    } else {
      vals.put(column, String.valueOf(newValue));
    }

    int rows = getContentResolver().update(
        ContactNotifications.buildContactUri(rowId), vals, null, null);
    return rows == 1 ? true : false;
  }

  private void initPreferences(Cursor c) {
    final String one = "1";
    String contactName = c.getString(c.getColumnIndex(NotificationsDatabase.CONTACT_NAME));

    CheckBoxPreference enabledPref = (CheckBoxPreference) findPreference(PREF_ENABLED);
    enabledPref.setChecked(
        one.equals(c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.ENABLED))));
    enabledPref.setSummaryOn(
            getString(R.string.contact_customization_enabled, contactName));
    enabledPref.setSummaryOff(
            getString(R.string.contact_customization_disabled, contactName));
    enabledPref.setOnPreferenceChangeListener(onPrefChangeListener);

    SoundPreference soundPref = (SoundPreference) findPreference(PREF_SOUND);
    soundPref.setOnPreferenceChangeListener(onPrefChangeListener);
    soundPref.setNotificationId(rowId);

    TestNotificationDialogPreference testPref = (TestNotificationDialogPreference) findPreference(PREF_TEST);
    testPref.setRowId(rowId);

    /*
     * Vibrate Prefs
     */
    CheckBoxPreference enableVibratePref =
            (CheckBoxPreference) findPreference(PREF_VIBRATE);
    enableVibratePref.setOnPreferenceChangeListener(onPrefChangeListener);
    enableVibratePref.setChecked(
        one.equals(c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.VIBRATE))));

    VibratePatternListPreference vibratePatternPref =
            (VibratePatternListPreference) findPreference(PREF_VIBRATE_PATTERN);
    vibratePatternPref.setValue(
        c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.VIBRATE_PATTERN)));
    vibratePatternPref.setOnPreferenceChangeListener(onPrefChangeListener);
    vibratePatternPref.setRowId(rowId);

    /*
     * LED Prefs
     */
    CheckBoxPreference ledEnabledPref =
        (CheckBoxPreference) findPreference(PREF_LED);
    ledEnabledPref.setChecked(
        one.equals(c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.LED))));
    ledEnabledPref.setOnPreferenceChangeListener(onPrefChangeListener);

    ListPreference ledColorPref =
        (ListPreference) findPreference(PREF_LED_COLOR);
    ledColorPref.setValue(
        c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.LED_COLOR)));
    ledColorPref.setOnPreferenceChangeListener(onPrefChangeListener);

    LedBlinkPatternListPreference ledPatternPref =
            (LedBlinkPatternListPreference) findPreference(PREF_LED_PATTERN);
    ledPatternPref.setValue(
        c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.LED_PATTERN)));
    ledPatternPref.setOnPreferenceChangeListener(onPrefChangeListener);
    ledPatternPref.setRowId(rowId);
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    if (rowId <= 0)
      return false;
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.config_notification, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case android.R.id.home:
      finish();
      break;
    case R.id.save_menu_item:
      finish();
      return true;
    case R.id.remove_menu_item:
      getContentResolver().delete(ContactNotifications.buildContactUri(rowId), null, null);
      finish();
      return true;
    }
    return false;
  }

  public String getContactName(Context context, String lookupKey, String contactId) {

    if (lookupKey == null) {
      return null;
    }

    Uri.Builder builder = Contacts.CONTENT_LOOKUP_URI.buildUpon();
    builder.appendPath(lookupKey);
    if (contactId != null) {
      builder.appendPath(contactId);
    }
    Uri uri = builder.build();

    Cursor cursor = context.getContentResolver().query(
        uri,
        new String[] { Contacts.DISPLAY_NAME },
        null, null, null);

    if (cursor != null) {
      try {
        if (cursor.moveToFirst()) {
          return cursor.getString(0).trim();
        }
      } finally {
        if (cursor != null) {
          cursor.close();
        }
      }
    }

    return null;
  }


  private Cursor createContact(String contactId, String contactLookupKey) {

    final String contactName = getContactName(this, contactLookupKey, contactId);

    if (contactName == null) {
      return null;
    }

    final ContentValues vals = new ContentValues();
    vals.put(NotificationsDatabase.CONTACT_NAME, contactName.trim());
    vals.put(NotificationsDatabase.CONTACT_ID, contactId);
    vals.put(NotificationsDatabase.CONTACT_LOOKUPKEY, contactLookupKey);

    final Uri contactUri = getContentResolver().insert(ContactNotifications.CONTENT_URI, vals);

    return getContentResolver().query(contactUri, null, null, null, null);

  }

  private Cursor createContact(Uri contactUri) {
    return createContact(
        ContactNotifications.getContactId(contactUri),
        ContactNotifications.getLookupKey(contactUri));
  }

}
