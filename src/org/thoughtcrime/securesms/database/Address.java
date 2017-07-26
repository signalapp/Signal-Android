package org.thoughtcrime.securesms.database;


import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.NumberUtil;
import org.thoughtcrime.securesms.util.ShortCodeUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.util.LinkedList;
import java.util.List;

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

  public static Address fromExternal(@NonNull Context context, @Nullable String external)
  {
    if (external == null) return new Address("Unknown");

    try {
      String localNumber = TextSecurePreferences.getLocalNumber(context);

      if      (GroupUtil.isEncodedGroup(external))               return new Address(external);
      else if (NumberUtil.isValidEmail(external))                return new Address(external);
      else if (ShortCodeUtil.isShortCode(localNumber, external)) return new Address(external.replaceAll("[^0-9+]", ""));

      return new Address(PhoneNumberFormatter.formatNumber(external, localNumber));
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      if (TextUtils.isEmpty(external.trim())) return new Address("Unknown");
      else                                    return new Address(external.trim());
    }
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
}
