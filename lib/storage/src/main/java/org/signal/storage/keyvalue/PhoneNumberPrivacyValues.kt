package org.signal.storage.keyvalue

import org.signal.core.util.logging.Log

class PhoneNumberPrivacyValues(store: KeyValueStore, private val isPhoneNumberless: () -> Boolean) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(PhoneNumberPrivacyValues::class)

    const val SHARING_MODE: String = "phoneNumberPrivacy.sharingMode"
    const val DISCOVERABILITY_MODE: String = "phoneNumberPrivacy.listingMode"
    const val DISCOVERABILITY_TIMESTAMP: String = "phoneNumberPrivacy.listingMode.timestamp"

    private val ACI_AND_E164_CERTIFICATE: Collection<CertificateType> = listOf(CertificateType.ACI_AND_E164)
    private val ACI_ONLY_CERTIFICATE: Collection<CertificateType> = listOf(CertificateType.ACI_ONLY)
    private val BOTH_CERTIFICATES: Collection<CertificateType> = listOf(CertificateType.ACI_AND_E164, CertificateType.ACI_ONLY)
  }

  public override fun onFirstEverAppLaunch() {
    store.beginWrite()
      .putInteger(DISCOVERABILITY_MODE, PhoneNumberDiscoverabilityMode.UNDECIDED.serialize())
      .apply()
  }

  override val keysToIncludeInBackup: List<String>
    get() = listOf(SHARING_MODE, DISCOVERABILITY_MODE, DISCOVERABILITY_TIMESTAMP)

  /**
   * Note: Only giving raw access to the underlying value for storage service.
   * Most callers should use [isPhoneNumberSharingEnabled].
   */
  var phoneNumberSharingMode: PhoneNumberSharingMode
    get() {
      if (isPhoneNumberless()) {
        return PhoneNumberSharingMode.NOBODY
      }

      return PhoneNumberSharingMode.deserialize(getInteger(SHARING_MODE, PhoneNumberSharingMode.DEFAULT.serialize()))
    }
    set(value) {
      Log.i(TAG, "Setting phone number sharing to: ${value.name}", Throwable())
      store.beginWrite().putInteger(SHARING_MODE, value.serialize()).commit()
    }

  val isPhoneNumberSharingEnabled: Boolean
    get() {
      return when (phoneNumberSharingMode) {
        PhoneNumberSharingMode.EVERYBODY -> {
          true
        }
        PhoneNumberSharingMode.DEFAULT, PhoneNumberSharingMode.NOBODY -> {
          false
        }
      }
    }

  var phoneNumberDiscoverabilityMode: PhoneNumberDiscoverabilityMode
    get() {
      if (isPhoneNumberless()) {
        return PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE
      }

      // The default for existing users is to be discoverable, but new users are set to UNDECIDED in onFirstEverAppLaunch
      return PhoneNumberDiscoverabilityMode.deserialize(getInteger(DISCOVERABILITY_MODE, PhoneNumberDiscoverabilityMode.DISCOVERABLE.serialize()))
    }
    set(value) {
      Log.i(TAG, "Setting phone number discoverability to: ${value.name}", Throwable())
      store.beginWrite()
        .putInteger(DISCOVERABILITY_MODE, value.serialize())
        .putLong(DISCOVERABILITY_TIMESTAMP, System.currentTimeMillis())
        .commit()
    }

  val phoneNumberDiscoverabilityModeTimestamp: Long by longValue(DISCOVERABILITY_TIMESTAMP, 0)

  /**
   * If you respect [phoneNumberSharingMode], then you will only ever need to fetch and store
   * these certificates types.
   */
  val requiredCertificateTypes: Collection<CertificateType>
    get() {
      return if (isPhoneNumberless()) {
        ACI_ONLY_CERTIFICATE
      } else if (isPhoneNumberSharingEnabled) {
        ACI_AND_E164_CERTIFICATE
      } else {
        ACI_ONLY_CERTIFICATE
      }
    }

  /**
   * All certificate types the account can fetch.
   */
  val allCertificateTypes: Collection<CertificateType>
    get() {
      return if (isPhoneNumberless()) {
        ACI_ONLY_CERTIFICATE
      } else {
        BOTH_CERTIFICATES
      }
    }

  enum class PhoneNumberSharingMode(private val code: Int) {
    DEFAULT(0),
    EVERYBODY(1),
    NOBODY(2);

    fun serialize(): Int = code

    companion object {
      @JvmStatic
      fun deserialize(code: Int): PhoneNumberSharingMode {
        return entries.firstOrNull { it.code == code } ?: throw IllegalArgumentException("Unrecognized code: $code")
      }
    }
  }

  enum class PhoneNumberDiscoverabilityMode(private val code: Int) {
    DISCOVERABLE(0),
    NOT_DISCOVERABLE(1),

    /** The user is going through registration and has not yet chosen a discoverability setting */
    UNDECIDED(2);

    fun serialize(): Int = code

    companion object {
      @JvmStatic
      fun deserialize(code: Int): PhoneNumberDiscoverabilityMode {
        return entries.firstOrNull { it.code == code } ?: throw IllegalArgumentException("Unrecognized code: $code")
      }
    }
  }
}
