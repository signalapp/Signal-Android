package org.thoughtcrime.securesms.database;


import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.phonenumbers.NumberUtil;

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

  public static @NonNull Address fromSerialized(@NonNull String serialized) {
    return new Address(serialized);
  }

  public boolean isGroup() {
    return GroupUtil.isEncodedGroup(address);
  }

  public boolean isMmsGroup() {
    return GroupUtil.isMmsGroup(address);
  }

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
    if (!isPhone()) {
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
}
