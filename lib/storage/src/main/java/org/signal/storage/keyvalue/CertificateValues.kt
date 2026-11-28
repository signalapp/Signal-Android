package org.signal.storage.keyvalue

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import kotlin.time.Duration.Companion.days

class CertificateValues(store: KeyValueStore, context: Context) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(CertificateValues::class)

    private const val SEALED_SENDER_CERT_ACI_AND_E164 = "certificate.uuidAndE164"
    private const val SEALED_SENDER_CERT_ACI_ONLY = "certificate.uuidOnly"
    private const val LAST_ROTATION_TIME = "certificate.lastRotationTime"

    private const val NEVER_ROTATED: Long = -1
    private val LEGACY_ROTATION_INTERVAL = 1.days.inWholeMilliseconds
  }

  init {
    if (!store.containsKey(LAST_ROTATION_TIME)) {
      migrateFromSharedPrefsV1(context)
    }
  }

  public override fun onFirstEverAppLaunch() = Unit

  override val keysToIncludeInBackup: List<String>
    get() = emptyList()

  @WorkerThread
  fun setUnidentifiedAccessCertificate(certificateType: CertificateType, certificate: ByteArray?) {
    val key = when (certificateType) {
      CertificateType.ACI_AND_E164 -> {
        SEALED_SENDER_CERT_ACI_AND_E164
      }
      CertificateType.ACI_ONLY -> {
        SEALED_SENDER_CERT_ACI_ONLY
      }
    }

    store.beginWrite().putBlob(key, certificate).commit()
  }

  fun getUnidentifiedAccessCertificate(certificateType: CertificateType): ByteArray? {
    return when (certificateType) {
      CertificateType.ACI_AND_E164 -> {
        getBlob(SEALED_SENDER_CERT_ACI_AND_E164, null)
      }
      CertificateType.ACI_ONLY -> {
        getBlob(SEALED_SENDER_CERT_ACI_ONLY, null)
      }
    }
  }

  var lastRotationTime: Long by longValue(LAST_ROTATION_TIME, NEVER_ROTATED)

  /**
   * Do not alter. If you need to migrate more stuff, create a new method.
   *
   * The legacy value held the next rotation time, always written as the clock at the last rotation plus a day.
   */
  private fun migrateFromSharedPrefsV1(context: Context) {
    Log.i(TAG, "[V1] Migrating certificate values from shared prefs.")

    val legacyNextRotationTime = LegacySharedPrefs.getLong(context, "pref_unidentified_access_certificate_rotation_time", 0)

    putLong(LAST_ROTATION_TIME, if (legacyNextRotationTime > 0) legacyNextRotationTime - LEGACY_ROTATION_INTERVAL else NEVER_ROTATED)
  }
}
