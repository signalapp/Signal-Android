package org.thoughtcrime.securesms.components.location;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.maps.model.LatLng;

import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.PlayServicesUtil;

import java.io.IOException;

public class SignalPlace {

  private static final String URL_OSM    = "https://www.openstreetmap.org/#map=15/%s/%s";
  private static final String URL_GOOGLE = "https://maps.google.com/maps";
  private static final String TAG        = SignalPlace.class.getSimpleName();

  @JsonProperty
  private CharSequence name;

  @JsonProperty
  private CharSequence address;

  @JsonProperty
  private double latitude;

  @JsonProperty
  private double longitude;

  public SignalPlace(Place place) {
    this(place.getName(), place.getAddress(), place.getLatLng().latitude, place.getLatLng().longitude);
  }

  public SignalPlace(CharSequence name, CharSequence address, double locationLat, double locationLong) {
    this.name      = name == null ? "" : name;
    this.address   = address == null ? "" : address;
    this.latitude  = locationLat;
    this.longitude = locationLong;
  }

  public SignalPlace() {}

  @JsonIgnore
  public LatLng getLatLong() {
    return new LatLng(latitude, longitude);
  }

  @JsonIgnore
  public String getDescription(Context context) {
    String description = "";

    if (!TextUtils.isEmpty(name)) {
      description += (name + "\n");
    }

    if (!TextUtils.isEmpty(address)) {
      description += (address + "\n");
    }

    if (PlayServicesUtil.getPlayServicesStatus(context) == PlayServicesUtil.PlayServicesStatus.SUCCESS) {
      description += Uri.parse(URL_GOOGLE)
                        .buildUpon()
                        .appendQueryParameter("q", String.format("%s,%s", latitude, longitude))
                        .build().toString();
    } else {
      description += String.format(URL_OSM, latitude, longitude);
    }

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
