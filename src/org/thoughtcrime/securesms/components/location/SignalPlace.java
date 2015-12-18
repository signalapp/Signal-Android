package org.thoughtcrime.securesms.components.location;

import android.text.TextUtils;

import com.google.android.gms.location.places.Place;
import com.google.android.gms.maps.model.LatLng;

public class SignalPlace {

  private static final String URL = "https://maps.google.com/maps?q=%s,%s";

  private final Place place;

  public SignalPlace(Place place) {
    this.place = place;
  }

  public LatLng getLatLong() {
    return place.getLatLng();
  }

  public String getDescription() {
    String description = "";

    if (!TextUtils.isEmpty(place.getName())) {
      description += (place.getName() + "\n");
    }

    if (!TextUtils.isEmpty(place.getAddress())) {
      description += (place.getAddress() + "\n");
    }

    description += String.format(URL, place.getLatLng().latitude, place.getLatLng().longitude);

    return description;
  }
}
