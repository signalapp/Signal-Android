package org.thoughtcrime.securesms.database;


import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.ShortNumberInfo;

import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.NumberUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class Address implements Parcelable, Comparable<Address> {

  public static final Parcelable.Creator<Address> CREATOR = new Parcelable.Creator<Address>() {
    public Address createFromParcel(Parcel in) {
      return new Address(in);
    }

    public Address[] newArray(int size) {
      return new Address[size];
    }
  };

  public static final Address UNKNOWN = new Address("Unknown");

  private static final String TAG = Address.class.getSimpleName();

  private final String address;

  private Address(@NonNull String address) {
    if (address == null) throw new AssertionError(address);
    this.address = address;
  }

  public Address(Parcel in) {
    this(in.readString());
  }

  public static Address fromSerialized(@NonNull String serialized) {
    return new Address(serialized);
  }

  public static List<Address> fromSerializedList(@NonNull String serialized, @NonNull String delimiter) {
    List<String>  elements  = Util.split(serialized, delimiter);
    List<Address> addresses = new LinkedList<>();

    for (String element : elements) {
      addresses.add(Address.fromSerialized(element));
    }

    return addresses;
  }

  public static Address fromExternal(@NonNull Context context, @Nullable String external) {
    return new Address(new ExternalAddressFormatter(TextSecurePreferences.getLocalNumber(context)).format(external));
  }

  public static Address[] fromParcelable(Parcelable[] parcelables) {
    Address[] addresses = new Address[parcelables.length];

    for (int i=0;i<parcelables.length;i++) {
      addresses[i] = (Address)parcelables[i];
    }

    return addresses;
  }

  public boolean isGroup() {
    return GroupUtil.isEncodedGroup(address);
  }

  public boolean isEmail() {
    return NumberUtil.isValidEmail(address);
  }

  public boolean isPhone() {
    return !isGroup() && !isEmail();
  }

  public @NonNull String toGroupString() {
    if (!isGroup()) throw new AssertionError("Not group: " + address);
    return address;
  }

  public @NonNull String toPhoneString() {
    if (!isPhone()) throw new AssertionError("Not e164: " + address);
    return address;
  }

  public @NonNull String toEmailString() {
    if (!isEmail()) throw new AssertionError("Not email: " + address);
    return address;
  }

  @Override
  public String toString() {
    return address;
  }

  public String serialize() {
    return address;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || !(other instanceof Address)) return false;
    return address.equals(((Address) other).address);
  }

  @Override
  public int hashCode() {
    return address.hashCode();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(address);
  }

  @Override
  public int compareTo(@NonNull Address other) {
    return address.compareTo(other.address);
  }

  @VisibleForTesting
  static class ExternalAddressFormatter {

    private static final String TAG = ExternalAddressFormatter.class.getSimpleName();

    private static final Set<String> SHORT_COUNTRIES = new HashSet<String>() {{
      add("NU");
      add("TK");
      add("NC");
      add("AC");
    }};

    private final Phonenumber.PhoneNumber localNumber;
    private final String                  localNumberString;
    private final String                  localCountryCode;

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    private final Pattern         ALPHA_PATTERN   = Pattern.compile("[a-zA-Z]");

    public ExternalAddressFormatter(String localNumber) {
      try {
        this.localNumberString = localNumber;
        this.localNumber       = phoneNumberUtil.parse(localNumber, null);
        this.localCountryCode  = phoneNumberUtil.getRegionCodeForNumber(this.localNumber);
      } catch (NumberParseException e) {
        throw new AssertionError(e);
      }
    }

    public String format(@Nullable String number) {
      if (number == null)                             return "Unknown";
      if (number.startsWith("__textsecure_group__!")) return number;
      if (ALPHA_PATTERN.matcher(number).matches())    return number.trim();

      String bareNumber = number.replaceAll("[^0-9+]", "");

      if (bareNumber.length() == 0) {
        if (number.trim().length() == 0) return "Unknown";
        else                             return number.trim();
      }

      // libphonenumber doesn't seem to be correct for Germany and Finland
      if (bareNumber.length() <= 6 && ("DE".equals(localCountryCode) || "FI".equals(localCountryCode) || "SK".equals(localCountryCode))) {
        return bareNumber;
      }

      // libphonenumber seems incorrect for Russia and a few other countries with 4 digit short codes.
      if (bareNumber.length() <= 4 && !SHORT_COUNTRIES.contains(localCountryCode)) {
        return bareNumber;
      }

      try {
        Phonenumber.PhoneNumber parsedNumber = phoneNumberUtil.parse(bareNumber, localCountryCode);

        if (ShortNumberInfo.getInstance().isPossibleShortNumberForRegion(parsedNumber, localCountryCode)) {
          return bareNumber;
        }

        return phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
      } catch (NumberParseException e) {
        Log.w(TAG, e);
        if (bareNumber.charAt(0) == '+')
          return bareNumber;

        String localNumberImprecise = localNumberString;

        if (localNumberImprecise.charAt(0) == '+')
          localNumberImprecise = localNumberImprecise.substring(1);

        if (localNumberImprecise.length() == bareNumber.length() || bareNumber.length() > localNumberImprecise.length())
          return "+" + number;

        int difference = localNumberImprecise.length() - bareNumber.length();

        return "+" + localNumberImprecise.substring(0, difference) + bareNumber;
      }
    }
  }

}
