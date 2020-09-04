package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.FeatureFlags;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public final class PhoneNumberPrivacyValues extends SignalStoreValues {

  public static final String SHARING_MODE = "phoneNumberPrivacy.sharingMode";
  public static final String LISTING_MODE = "phoneNumberPrivacy.listingMode";

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
    //          .putInteger(LISTING_MODE, PhoneNumberListingMode.UNLISTED.ordinal())
    //          .apply();
  }

  public @NonNull PhoneNumberSharingMode getPhoneNumberSharingMode() {
    if (!FeatureFlags.phoneNumberPrivacy()) return PhoneNumberSharingMode.EVERYONE;
    return PhoneNumberSharingMode.values()[getInteger(SHARING_MODE, PhoneNumberSharingMode.EVERYONE.ordinal())];
  }

  public void setPhoneNumberSharingMode(@NonNull PhoneNumberSharingMode phoneNumberSharingMode) {
    putInteger(SHARING_MODE, phoneNumberSharingMode.ordinal());
  }

  public @NonNull PhoneNumberListingMode getPhoneNumberListingMode() {
    if (!FeatureFlags.phoneNumberPrivacy()) return PhoneNumberListingMode.LISTED;
    return PhoneNumberListingMode.values()[getInteger(LISTING_MODE, PhoneNumberListingMode.LISTED.ordinal())];
  }

  public void setPhoneNumberListingMode(@NonNull PhoneNumberListingMode phoneNumberListingMode) {
    putInteger(LISTING_MODE, phoneNumberListingMode.ordinal());
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

  /**
   * Serialized, do not change ordinal/order
   */
  public enum PhoneNumberSharingMode {
    EVERYONE,
    CONTACTS,
    NOBODY
  }

  /**
   * Serialized, do not change ordinal/order
   */
  public enum PhoneNumberListingMode {
    LISTED,
    UNLISTED;

    public boolean isDiscoverable() {
      return this == LISTED;
    }

    public boolean isUnlisted() {
      return this == UNLISTED;
    }
  }
}
