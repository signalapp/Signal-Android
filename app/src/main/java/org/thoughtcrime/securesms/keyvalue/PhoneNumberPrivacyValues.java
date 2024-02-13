package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class PhoneNumberPrivacyValues extends SignalStoreValues {

  public static final String SHARING_MODE              = "phoneNumberPrivacy.sharingMode";
  public static final String DISCOVERABILITY_MODE      = "phoneNumberPrivacy.listingMode";
  public static final String DISCOVERABILITY_TIMESTAMP = "phoneNumberPrivacy.listingMode.timestamp";

  private static final Collection<CertificateType> ACI_AND_E164_CERTIFICATE = Collections.singletonList(CertificateType.ACI_AND_E164);
  private static final Collection<CertificateType> ACI_ONLY_CERTIFICATE     = Collections.singletonList(CertificateType.ACI_ONLY);
  private static final Collection<CertificateType> BOTH_CERTIFICATES        = Collections.unmodifiableCollection(Arrays.asList(CertificateType.ACI_AND_E164, CertificateType.ACI_ONLY));

  PhoneNumberPrivacyValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    // TODO [ALAN] PhoneNumberPrivacy: During registration, set the attribute to so that new registrations start out as not listed
    //getStore().beginWrite()
    //          .putInteger(LISTING_MODE, PhoneNumberListingMode.UNLISTED.serialize())
    //          .apply();
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Arrays.asList(SHARING_MODE, DISCOVERABILITY_MODE, DISCOVERABILITY_TIMESTAMP);
  }

  /**
   * Note: Only giving raw access to the underlying value for storage service.
   * Most callers should use {@link #isPhoneNumberSharingEnabled()}.
   */
  public @NonNull PhoneNumberSharingMode getPhoneNumberSharingMode() {
    return PhoneNumberSharingMode.deserialize(getInteger(SHARING_MODE, PhoneNumberSharingMode.DEFAULT.serialize()));
  }

  public boolean isPhoneNumberSharingEnabled() {
    // TODO [pnp] When we launch usernames, the default should return false
    return switch (getPhoneNumberSharingMode()) {
      case DEFAULT, EVERYBODY -> true;
      case NOBODY -> false;
    };
  }

  public void setPhoneNumberSharingMode(@NonNull PhoneNumberSharingMode phoneNumberSharingMode) {
    putInteger(SHARING_MODE, phoneNumberSharingMode.serialize());
  }

  public boolean isDiscoverableByPhoneNumber() {
    return getPhoneNumberDiscoverabilityMode() == PhoneNumberDiscoverabilityMode.DISCOVERABLE;
  }

  public @NonNull PhoneNumberDiscoverabilityMode getPhoneNumberDiscoverabilityMode() {
    return PhoneNumberDiscoverabilityMode.deserialize(getInteger(DISCOVERABILITY_MODE, PhoneNumberDiscoverabilityMode.DISCOVERABLE.serialize()));
  }

  public void setPhoneNumberDiscoverabilityMode(@NonNull PhoneNumberDiscoverabilityMode phoneNumberDiscoverabilityMode) {
    getStore()
        .beginWrite()
        .putInteger(DISCOVERABILITY_MODE, phoneNumberDiscoverabilityMode.serialize())
        .putLong(DISCOVERABILITY_TIMESTAMP, System.currentTimeMillis())
        .apply();
  }

  public long getPhoneNumberDiscoverabilityModeTimestamp() {
    return getLong(DISCOVERABILITY_TIMESTAMP, 0);
  }

  /**
   * If you respect {@link #getPhoneNumberSharingMode}, then you will only ever need to fetch and store
   * these certificates types.
   */
  public Collection<CertificateType> getRequiredCertificateTypes() {
    if (isPhoneNumberSharingEnabled()) {
      return ACI_AND_E164_CERTIFICATE;
    } else {
      return ACI_ONLY_CERTIFICATE;
    }
  }

  /**
   * All certificate types required according to the feature flags.
   */
  public Collection<CertificateType> getAllCertificateTypes() {
    return BOTH_CERTIFICATES;
  }

  public enum PhoneNumberSharingMode {
    DEFAULT(0),
    EVERYBODY(1),
    NOBODY(2);

    private final int code;

    PhoneNumberSharingMode(int code) {
      this.code = code;
    }

    public int serialize() {
      return code;
    }

    public static PhoneNumberSharingMode deserialize(int code) {
      for (PhoneNumberSharingMode value : PhoneNumberSharingMode.values()) {
        if (value.code == code) {
          return value;
        }
      }

      throw new IllegalArgumentException("Unrecognized code: " + code);
    }
  }

  public enum PhoneNumberDiscoverabilityMode {
    DISCOVERABLE(0),
    NOT_DISCOVERABLE(1);

    private final int code;

    PhoneNumberDiscoverabilityMode(int code) {
      this.code = code;
    }

    public boolean isDiscoverable() {
      return this == DISCOVERABLE;
    }

    public boolean isUndiscoverable() {
      return this == NOT_DISCOVERABLE;
    }

    public int serialize() {
      return code;
    }

    public static PhoneNumberDiscoverabilityMode deserialize(int code) {
      for (PhoneNumberDiscoverabilityMode value : PhoneNumberDiscoverabilityMode.values()) {
        if (value.code == code) {
          return value;
        }
      }

      throw new IllegalArgumentException("Unrecognized code: " + code);
    }
  }
}
