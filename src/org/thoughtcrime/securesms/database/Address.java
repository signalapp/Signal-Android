package org.thoughtcrime.securesms.database;


import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Pair;

import org.thoughtcrime.securesms.util.DelimiterUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.NumberUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
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

  private static final AtomicReference<Pair<String, ExternalAddressFormatter>> cachedFormatter = new AtomicReference<>();

  private final String address;

  private Address(@NonNull String address) {
    if (address == null) throw new AssertionError(address);
    this.address = address.toLowerCase();
  }

  public Address(Parcel in) {
    this(in.readString());
  }

  public static @NonNull Address fromSerialized(@NonNull String serialized) {
    return new Address(serialized);
  }

  public static Address fromExternal(@NonNull Context context, @Nullable String external) {
    return Address.fromSerialized(external);
  }

  public static @NonNull List<Address> fromSerializedList(@NonNull String serialized, char delimiter) {
    String[]      escapedAddresses = DelimiterUtil.split(serialized, delimiter);
    List<Address> addresses        = new LinkedList<>();

    for (String escapedAddress : escapedAddresses) {
      addresses.add(Address.fromSerialized(DelimiterUtil.unescape(escapedAddress, delimiter)));
    }

    return addresses;
  }

  public static @NonNull String toSerializedList(@NonNull List<Address> addresses, char delimiter) {
    Collections.sort(addresses);

    List<String> escapedAddresses = new LinkedList<>();

    for (Address address : addresses) {
      escapedAddresses.add(DelimiterUtil.escape(address.serialize(), delimiter));
    }

    return Util.join(escapedAddresses, delimiter + "");
  }

  private static @NonNull ExternalAddressFormatter getExternalAddressFormatter(Context context) {
    String localNumber = TextSecurePreferences.getLocalNumber(context);

    if (!TextUtils.isEmpty(localNumber)) {
      Pair<String, ExternalAddressFormatter> cached = cachedFormatter.get();

      if (cached != null && cached.first.equals(localNumber)) return cached.second;

      ExternalAddressFormatter formatter = new ExternalAddressFormatter(localNumber);
      cachedFormatter.set(new Pair<>(localNumber, formatter));

      return formatter;
    } else {
      return new ExternalAddressFormatter(Util.getSimCountryIso(context).or("US"), true);
    }
  }

  public boolean isGroup() { return GroupUtil.isEncodedGroup(address); }

  public boolean isSignalGroup() { return GroupUtil.isSignalGroup(address); }

  public boolean isPublicChat() { return GroupUtil.isPublicChat(address); }

  public boolean isRSSFeed() { return GroupUtil.isRssFeed(address); }

  public boolean isMmsGroup() { return GroupUtil.isMmsGroup(address); }

  public boolean isEmail() {
    return NumberUtil.isValidEmail(address);
  }

  public boolean isPhone() {
    return !isGroup() && !isEmail();
  }

  public @NonNull String toGroupString() {
    if (!isGroup()) throw new AssertionError("Not group");
    return address;
  }

  public @NonNull String toPhoneString() {
    if (!isPhone() && !isPublicChat()) {
      if (isEmail()) throw new AssertionError("Not e164, is email");
      if (isGroup()) throw new AssertionError("Not e164, is group");
      throw new AssertionError("Not e164, unknown");
    }
    return address;
  }

  public @NonNull String toEmailString() {
    if (!isEmail()) throw new AssertionError("Not email");
    return address;
  }

  @Override
  public @NonNull String toString() {
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
  public static class ExternalAddressFormatter {

    private static final String TAG = ExternalAddressFormatter.class.getSimpleName();

    private static final Set<String> SHORT_COUNTRIES = new HashSet<String>() {{
      add("NU");
      add("TK");
      add("NC");
      add("AC");
    }};

    private static final Pattern US_NO_AREACODE = Pattern.compile("^(\\d{7})$");
    private static final Pattern BR_NO_AREACODE = Pattern.compile("^(9?\\d{8})$");

    private final Optional<PhoneNumber> localNumber;
    private final String                localCountryCode;

    private final Pattern         ALPHA_PATTERN   = Pattern.compile("[a-zA-Z]");

    ExternalAddressFormatter(@NonNull String localNumberString) {
      throw new AssertionError("Not Implemented");
    }

    ExternalAddressFormatter(@NonNull String localCountryCode, boolean countryCode) {
      this.localNumber      = Optional.absent();
      this.localCountryCode = localCountryCode;
    }

    public String format(@Nullable String number) {
      if (number == null)                       return "Unknown";
      return number;
    }

    private @Nullable String parseAreaCode(@NonNull String e164Number, int countryCode) {
      switch (countryCode) {
        case 1:
          return e164Number.substring(2, 5);
        case 55:
          return e164Number.substring(3, 5);
      }
      return null;
    }


    private @NonNull String applyAreaCodeRules(@NonNull Optional<PhoneNumber> localNumber, @NonNull String testNumber) {
      if (!localNumber.isPresent() || !localNumber.get().getAreaCode().isPresent()) {
        return testNumber;
      }

      Matcher matcher;
      switch (localNumber.get().getCountryCode()) {
        case 1:
          matcher = US_NO_AREACODE.matcher(testNumber);
          if (matcher.matches()) {
            return localNumber.get().getAreaCode() + matcher.group();
          }
          break;

        case 55:
          matcher = BR_NO_AREACODE.matcher(testNumber);
          if (matcher.matches()) {
            return localNumber.get().getAreaCode() + matcher.group();
          }
      }
      return testNumber;
    }

    private static class PhoneNumber {
      private final String           e164Number;
      private final int              countryCode;
      private final Optional<String> areaCode;

      PhoneNumber(String e164Number, int countryCode, @Nullable String areaCode) {
        this.e164Number  = e164Number;
        this.countryCode = countryCode;
        this.areaCode    = Optional.fromNullable(areaCode);
      }

      String getE164Number() {
        return e164Number;
      }

      int getCountryCode() {
        return countryCode;
      }

      Optional<String> getAreaCode() {
        return areaCode;
      }
    }
  }
}
