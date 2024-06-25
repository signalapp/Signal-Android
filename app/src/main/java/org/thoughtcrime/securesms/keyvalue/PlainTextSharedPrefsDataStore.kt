package org.thoughtcrime.securesms.keyvalue

import android.annotation.SuppressLint
import android.content.Context
import androidx.preference.PreferenceManager

/**
 * There are some values that you can't, for whatever reason, store in the normal encrypted [KeyValueStore].
 * Usually, it's because the value your storing is _related to_ the database. Regardless, this is just a normal
 * shared-prefs-backed class. Do not put anything in here that you wouldn't be comfortable storing in plain text.
 *
 * A good rule of thumb might be: if you're not comfortable logging it, then you shouldn't be comfortable putting
 * it in here.
 */
class PlainTextSharedPrefsDataStore(private val context: Context) {

  companion object {
    const val SMS_MIGRATION_ID_OFFSET = "sms_migration_id_offset"
  }

  private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

  /**
   * Stores the ID offset that was determined during the big migration that moved all SMS messages into the MMS table.
   */
  var smsMigrationIdOffset: Long
    get() = sharedPrefs.getLong(SMS_MIGRATION_ID_OFFSET, -1)

    @SuppressLint("ApplySharedPref")
    set(value) {
      sharedPrefs.edit().putLong(SMS_MIGRATION_ID_OFFSET, value).commit()
    }
}
