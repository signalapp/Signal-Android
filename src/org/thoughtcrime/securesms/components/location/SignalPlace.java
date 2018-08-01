package org.thoughtcrime.securesms.components.location;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.maps.model.LatLng;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;

public class SignalPlace {

  private static final String URL = "https://maps.google.com/maps";
  private static final String TAG = SignalPlace.class.getSimpleName();

  @JsonProperty
  private CharSequence name;

  @JsonProperty
  private CharSequence address;

  @JsonProperty
  private double latitude;

  @JsonProperty
  private double longitude;

  public SignalPlace(Place place) {
    this.name      = place.getName();
    this.address   = place.getAddress();
    this.latitude  = place.getLatLng().latitude;
    this.longitude = place.getLatLng().longitude;
  }

  public SignalPlace() {}

  @JsonIgnore
  public LatLng getLatLong() {
    return new LatLng(latitude, longitude);
  }

  @JsonIgnore
  public String getDescription() {
    String description = "";

    if (!TextUtils.isEmpty(name)) {
      description += (name + "\n");
    }

    if (!TextUtils.isEmpty(address)) {
      description += (address + "\n");
    }

    description += Uri.parse(URL)
                      .buildUpon()
                      .appendQueryParameter("q", String.format("%s,%s", latitude, longitude))
                      .build().toString();

    return description;
  }

  public @Nullable String serialize() {
    try {
      return JsonUtils.toJson(this);
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  public static SignalPlace deserialize(@NonNull  String serialized) throws IOException {
    return JsonUtils.fromJson(serialized, SignalPlace.class);
  }
}
