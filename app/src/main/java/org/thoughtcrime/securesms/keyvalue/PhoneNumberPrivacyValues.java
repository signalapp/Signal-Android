package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class PhoneNumberPrivacyValues extends SignalStoreValues {

  public static final String SHARING_MODE      = "phoneNumberPrivacy.sharingMode";
  public static final String LISTING_MODE      = "phoneNumberPrivacy.listingMode";
  public static final String LISTING_TIMESTAMP = "phoneNumberPrivacy.listingMode.timestamp";

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
    return Arrays.asList(SHARING_MODE, LISTING_MODE, LISTING_TIMESTAMP);
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
    return getPhoneNumberListingMode() == PhoneNumberPrivacyValues.PhoneNumberListingMode.LISTED;
  }

  public @NonNull PhoneNumberListingMode getPhoneNumberListingMode() {
    return PhoneNumberListingMode.deserialize(getInteger(LISTING_MODE, PhoneNumberListingMode.LISTED.serialize()));
  }

  public void setPhoneNumberListingMode(@NonNull PhoneNumberListingMode phoneNumberListingMode) {
    getStore()
        .beginWrite()
        .putInteger(LISTING_MODE, phoneNumberListingMode.serialize())
        .putLong(LISTING_TIMESTAMP, System.currentTimeMillis())
        .apply();
  }

  public long getPhoneNumberListingModeTimestamp() {
    return getLong(LISTING_TIMESTAMP, 0);
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

  public enum PhoneNumberListingMode {
    LISTED(0),
    UNLISTED(1);

    private final int code;

    PhoneNumberListingMode(int code) {
      this.code = code;
    }

    public boolean isDiscoverable() {
      return this == LISTED;
    }

    public boolean isUnlisted() {
      return this == UNLISTED;
    }

    public int serialize() {
      return code;
    }

    public static PhoneNumberListingMode deserialize(int code) {
      for (PhoneNumberListingMode value : PhoneNumberListingMode.values()) {
        if (value.code == code) {
          return value;
        }
      }

      throw new IllegalArgumentException("Unrecognized code: " + code);
    }
  }
}
