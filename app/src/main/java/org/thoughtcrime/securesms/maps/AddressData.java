package org.thoughtcrime.securesms.maps;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public final class AddressData implements Parcelable {

  private final double latitude;
  private final double longitude;
  private final String address;

  AddressData(double latitude, double longitude, @NonNull String address) {
    this.latitude  = latitude;
    this.longitude = longitude;
    this.address   = address;
  }

  public @NonNull String getAddress() {
    return address;
  }

  public double getLongitude() {
    return longitude;
  }

  public double getLatitude() {
    return latitude;
  }

  public static final Creator<AddressData> CREATOR = new Creator<AddressData>() {
    @Override
    public AddressData createFromParcel(Parcel in) {
      //noinspection ConstantConditions
      return new AddressData(in.readDouble(),
                             in.readDouble(),
                             in.readString());
    }

    @Override
    public AddressData[] newArray(int size) {
      return new AddressData[size];
    }
  };

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeDouble(latitude);
    dest.writeDouble(longitude);
    dest.writeString(address);
  }

  @Override
  public int describeContents() {
    return 0;
  }
}
