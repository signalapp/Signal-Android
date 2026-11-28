package org.signal.storage.keyvalue

import android.content.Context
import org.signal.core.util.logging.Log

/**
 * Specifically handles just the UI/UX state around PINs. For actual keys, see [SvrValues].
 */
class PinValues(store: KeyValueStore, context: Context, private val defaultReminderInterval: Long) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(PinValues::class)

    private const val LAST_SUCCESSFUL_ENTRY = "pin.last_successful_entry"
    private const val LAST_REMINDER_TIME = "pin.last_reminder_time"
    private const val NEXT_INTERVAL = "pin.interval_index"
    private const val MIGRATION_VERSION = "pin.migration_version"
    private const val PIN_REMINDERS_ENABLED = "pin.pin_reminders_enabled"

    private const val CURRENT_MIGRATION_VERSION = 1
  }

  init {
    if (getLong(MIGRATION_VERSION, 0) < CURRENT_MIGRATION_VERSION) {
      migrateFromSharedPrefsV1(context)
    }
  }

  public override fun onFirstEverAppLaunch() = Unit

  override val keysToIncludeInBackup: List<String>
    get() = listOf(PIN_REMINDERS_ENABLED)

  var nextReminderInterval: Long by longValue(NEXT_INTERVAL, defaultReminderInterval)

  var lastSuccessfulEntryTime: Long by longValue(LAST_SUCCESSFUL_ENTRY, 0)

  val lastReminderTime: Long
    get() = getLong(LAST_REMINDER_TIME, lastSuccessfulEntryTime)

  @get:JvmName("arePinRemindersEnabled")
  var pinRemindersEnabled: Boolean by booleanValue(PIN_REMINDERS_ENABLED, true)

  fun recordSuccessfulEntry(nextInterval: Long) {
    val now = System.currentTimeMillis()

    store.beginWrite()
      .putLong(LAST_SUCCESSFUL_ENTRY, now)
      .putLong(NEXT_INTERVAL, nextInterval)
      .putLong(LAST_REMINDER_TIME, now)
      .apply()
  }

  fun recordSkippedEntry(nextInterval: Long) {
    store.beginWrite()
      .putLong(NEXT_INTERVAL, nextInterval)
      .putLong(LAST_REMINDER_TIME, System.currentTimeMillis())
      .apply()
  }

  fun resetReminders(nextInterval: Long) {
    val now = System.currentTimeMillis()

    store.beginWrite()
      .putLong(NEXT_INTERVAL, nextInterval)
      .putLong(LAST_SUCCESSFUL_ENTRY, now)
      .putLong(LAST_REMINDER_TIME, now)
      .apply()
  }

  fun setNextReminderIntervalToAtMost(maxInterval: Long) {
    if (store.getLong(NEXT_INTERVAL, 0) > maxInterval) {
      putLong(NEXT_INTERVAL, maxInterval)
    }
  }

  /**
   * Unlike the other stores, we can't use one of the migrated keys as the "has this run?" marker: both [NEXT_INTERVAL] and
   * [LAST_SUCCESSFUL_ENTRY] are keys released versions already write, so their presence says nothing about whether this
   * migration ran. Hence the explicit [MIGRATION_VERSION].
   *
   * These values used to be read straight out of shared prefs as the *default* for the keys above, so only migrate the ones this
   * store doesn't already have a value for.
   *
   * Do not alter. If you need to migrate more stuff, bump [CURRENT_MIGRATION_VERSION] and add a new method.
   */
  private fun migrateFromSharedPrefsV1(context: Context) {
    Log.i(TAG, "[V1] Migrating pin values from shared prefs.")

    val writer = store.beginWrite()

    if (!store.containsKey(LAST_SUCCESSFUL_ENTRY)) {
      writer.putLong(LAST_SUCCESSFUL_ENTRY, LegacySharedPrefs.getLong(context, "pref_registration_lock_last_reminder_time_post_kbs", 0))
    }

    if (!store.containsKey(NEXT_INTERVAL)) {
      writer.putLong(NEXT_INTERVAL, LegacySharedPrefs.getLong(context, "pref_registration_lock_next_reminder_interval", defaultReminderInterval))
    }

    writer
      .putLong(MIGRATION_VERSION, CURRENT_MIGRATION_VERSION.toLong())
      .commit()
  }
}
