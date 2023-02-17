package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.FeatureFlags;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class PhoneNumberPrivacyValues extends SignalStoreValues {

  public static final String SHARING_MODE         = "phoneNumberPrivacy.sharingMode";
  public static final String LISTING_MODE         = "phoneNumberPrivacy.listingMode";
  public static final String LISTING_TIMESTAMP    = "phoneNumberPrivacy.listingMode.timestamp";
  public static final String USERNAME_OUT_OF_SYNC = "phoneNumberPrivacy.usernameOutOfSync";

  private static final Collection<CertificateType> REGULAR_CERTIFICATE = Collections.singletonList(CertificateType.UUID_AND_E164);
  private static final Collection<CertificateType> PRIVACY_CERTIFICATE = Collections.singletonList(CertificateType.UUID_ONLY);
  private static final Collection<CertificateType> BOTH_CERTIFICATES   = Collections.unmodifiableCollection(Arrays.asList(CertificateType.UUID_AND_E164, CertificateType.UUID_ONLY));

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

  public @NonNull PhoneNumberSharingMode getPhoneNumberSharingMode() {
    if (!FeatureFlags.phoneNumberPrivacy()) {
      return PhoneNumberSharingMode.EVERYONE;
    }

    return PhoneNumberSharingMode.deserialize(getInteger(SHARING_MODE, PhoneNumberSharingMode.EVERYONE.serialize()));
  }

  public void setPhoneNumberSharingMode(@NonNull PhoneNumberSharingMode phoneNumberSharingMode) {
    putInteger(SHARING_MODE, phoneNumberSharingMode.serialize());
  }

  public @NonNull PhoneNumberListingMode getPhoneNumberListingMode() {
    if (!FeatureFlags.phoneNumberPrivacy()) {
      return PhoneNumberListingMode.LISTED;
    }

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

  public void markUsernameOutOfSync() {
    putBoolean(USERNAME_OUT_OF_SYNC, true);
  }

  public void clearUsernameOutOfSync() {
    putBoolean(USERNAME_OUT_OF_SYNC, false);
  }

  public boolean isUsernameOutOfSync() {
    return getBoolean(USERNAME_OUT_OF_SYNC, false);
  }

  /**
   * If you respect {@link #getPhoneNumberSharingMode}, then you will only ever need to fetch and store
   * these certificates types.
   */
  public Collection<CertificateType> getRequiredCertificateTypes() {
    switch (getPhoneNumberSharingMode()) {
      case EVERYONE: return REGULAR_CERTIFICATE;
      case CONTACTS: return BOTH_CERTIFICATES;
      case NOBODY  : return PRIVACY_CERTIFICATE;
      default      : throw new AssertionError();
    }
  }

  /**
   * All certificate types required according to the feature flags.
   */
  public Collection<CertificateType> getAllCertificateTypes() {
    return FeatureFlags.phoneNumberPrivacy() ? BOTH_CERTIFICATES : REGULAR_CERTIFICATE;
  }

  public enum PhoneNumberSharingMode {
    EVERYONE(0),
    CONTACTS(1),
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
