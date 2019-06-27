package org.thoughtcrime.securesms.maps;

import android.location.Address;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;

public final class AddressData implements Parcelable {

  private final           double latitude;
  private final           double longitude;
  private final @Nullable Address address;

  AddressData(double latitude, double longitude, @Nullable Address address) {
    this.latitude  = latitude;
    this.longitude = longitude;
    this.address   = address;
  }

  public @Nullable Address getAddress() {
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
      return new AddressData(in.readDouble(),
                             in.readDouble(),
                             Address.CREATOR.createFromParcel(in));
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
    dest.writeParcelable(address, flags);
  }

  @Override
  public int describeContents() {
    return 0;
  }
}
