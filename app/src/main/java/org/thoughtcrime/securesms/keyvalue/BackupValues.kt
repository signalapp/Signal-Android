package org.thoughtcrime.securesms.keyvalue

import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.util.logging.Log
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

internal class BackupValues(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    val TAG = Log.tag(BackupValues::class.java)
    val KEY_CREDENTIALS = "backup.credentials"
  }

  override fun onFirstEverAppLaunch() = Unit
  override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  /**
   * Retrieves the stored credentials, mapped by the day they're valid. The day is represented as
   * the unix time (in seconds) of the start of the day. Wrapped in a [ArchiveServiceCredentials]
   * type to make it easier to use. See [ArchiveServiceCredentials.getForCurrentTime].
   */
  val credentialsByDay: ArchiveServiceCredentials
    get() {
      val serialized = store.getString(KEY_CREDENTIALS, null) ?: return ArchiveServiceCredentials()

      return try {
        val map = JsonUtil.fromJson(serialized, SerializedCredentials::class.java).credentialsByDay
        ArchiveServiceCredentials(map)
      } catch (e: IOException) {
        Log.w(TAG, "Invalid JSON! Clearing.", e)
        putString(KEY_CREDENTIALS, null)
        ArchiveServiceCredentials()
      }
    }

  /**
   * Adds the given credentials to the existing list of stored credentials.
   */
  fun addCredentials(credentials: List<ArchiveServiceCredential>) {
    val current: MutableMap<Long, ArchiveServiceCredential> = credentialsByDay.toMutableMap()
    current.putAll(credentials.associateBy { it.redemptionTime })
    putString(KEY_CREDENTIALS, JsonUtil.toJson(SerializedCredentials(current)))
  }

  /**
   * Trims out any credentials that are for days older than the given timestamp.
   */
  fun clearCredentialsOlderThan(startOfDayInSeconds: Long) {
    val current: MutableMap<Long, ArchiveServiceCredential> = credentialsByDay.toMutableMap()
    val updated = current.filterKeys { it < startOfDayInSeconds }
    putString(KEY_CREDENTIALS, JsonUtil.toJson(SerializedCredentials(updated)))
  }

  class SerializedCredentials(
    @JsonProperty
    val credentialsByDay: Map<Long, ArchiveServiceCredential>
  )

  /**
   * A [Map] wrapper that makes it easier to get the credential for the current time.
   */
  class ArchiveServiceCredentials(map: Map<Long, ArchiveServiceCredential>) : Map<Long, ArchiveServiceCredential> by map {
    constructor() : this(mapOf())

    /**
     * Retrieves a credential that is valid for the current time, otherwise null.
     */
    fun getForCurrentTime(currentTime: Duration): ArchiveServiceCredential? {
      val startOfDayInSeconds: Long = currentTime.inWholeDays.days.inWholeSeconds
      return this[startOfDayInSeconds]
    }
  }
}
