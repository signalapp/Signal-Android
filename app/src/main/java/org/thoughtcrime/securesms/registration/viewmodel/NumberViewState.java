package org.thoughtcrime.securesms.registration.viewmodel;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.signal.core.util.E164Util;

import java.util.Objects;

public final class NumberViewState implements Parcelable {

  public static final NumberViewState INITIAL = new Builder().build();

  private final String selectedCountryName;
  private final int    countryCode;
  private final String nationalNumber;

  private NumberViewState(Builder builder) {
    this.selectedCountryName = builder.countryDisplayName;
    this.countryCode         = builder.countryCode;
    this.nationalNumber      = builder.nationalNumber;
  }

  public Builder toBuilder() {
    return new Builder().countryCode(countryCode)
                        .selectedCountryDisplayName(selectedCountryName)
                        .nationalNumber(nationalNumber);
  }

  public int getCountryCode() {
    return countryCode;
  }

  public String getNationalNumber() {
    return nationalNumber;
  }

  public @Nullable String getCountryDisplayName() {
    if (selectedCountryName != null) {
      return selectedCountryName;
    }

    PhoneNumberUtil util = PhoneNumberUtil.getInstance();

    if (isValid()) {
      String actualCountry = getActualCountry(util, getE164Number());

      if (actualCountry != null) {
        return actualCountry;
      }
    }

    String regionCode = util.getRegionCodeForCountryCode(countryCode);
    return E164Util.getRegionDisplayName(regionCode).orElse(null);
  }

  /**
   * Finds actual name of region from a valid number. So for example +1 might map to US or Canada or other territories.
   */
  private static @Nullable String getActualCountry(@NonNull PhoneNumberUtil util, @NonNull String e164Number) {
    try {
      Phonenumber.PhoneNumber phoneNumber = getPhoneNumber(util, e164Number);
      String                  regionCode  = util.getRegionCodeForNumber(phoneNumber);

      if (regionCode != null) {
        return E164Util.getRegionDisplayName(regionCode).orElse(null);
      }

    } catch (NumberParseException e) {
      return null;
    }
    return null;
  }

  public boolean isValid() {
    return E164Util.isValidNumberForRegistration(Integer.toString(getCountryCode()), getE164Number());
  }

  @Override
  public int hashCode() {
    int hash = countryCode;
    hash *= 31;
    hash += nationalNumber != null ? nationalNumber.hashCode() : 0;
    hash *= 31;
    hash += selectedCountryName != null ? selectedCountryName.hashCode() : 0;
    return hash;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == null) return false;
    if (obj.getClass() != getClass()) return false;

    NumberViewState other = (NumberViewState) obj;

    return other.countryCode == countryCode &&
           Objects.equals(other.nationalNumber, nationalNumber) &&
           Objects.equals(other.selectedCountryName, selectedCountryName);
  }

  public String getE164Number() {
    return getConfiguredE164Number(countryCode, nationalNumber);
  }

  public String getFullFormattedNumber() {
    return formatNumber(PhoneNumberUtil.getInstance(), getE164Number());
  }

  private static String formatNumber(@NonNull PhoneNumberUtil util, @NonNull String e164Number) {
    try {
      Phonenumber.PhoneNumber number = getPhoneNumber(util, e164Number);
      return util.format(number, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
    } catch (NumberParseException e) {
      return e164Number;
    }
  }

  private static String getConfiguredE164Number(int countryCode, String number) {
    return E164Util.formatAsE164WithCountryCodeForDisplay(String.valueOf(countryCode), number);
  }

  private static Phonenumber.PhoneNumber getPhoneNumber(@NonNull PhoneNumberUtil util, @NonNull String e164Number)
    throws NumberParseException
  {
    return util.parse(e164Number, null);
  }

  public static class Builder {
    private String countryDisplayName;
    private int    countryCode;
    private String nationalNumber;

    public Builder countryCode(int countryCode) {
      this.countryCode = countryCode;
      return this;
    }

    public Builder selectedCountryDisplayName(String countryDisplayName) {
      this.countryDisplayName = countryDisplayName;
      return this;
    }

    public Builder nationalNumber(String nationalNumber) {
      this.nationalNumber = nationalNumber;
      return this;
    }

    public NumberViewState build() {
      return new NumberViewState(this);
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel parcel, int i) {
    parcel.writeString(selectedCountryName);
    parcel.writeInt(countryCode);
    parcel.writeString(nationalNumber);
  }

  public static final Creator<NumberViewState> CREATOR = new Creator<NumberViewState>() {
    @Override
    public NumberViewState createFromParcel(Parcel in) {
      return new Builder().selectedCountryDisplayName(in.readString())
                          .countryCode(in.readInt())
                          .nationalNumber(in.readString())
                          .build();
    }

    @Override
    public NumberViewState[] newArray(int size) {
      return new NumberViewState[size];
    }
  };
}
