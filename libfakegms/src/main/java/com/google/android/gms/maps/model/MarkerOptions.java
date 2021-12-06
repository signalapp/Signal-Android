package com.google.android.gms.maps.model;

public final class MarkerOptions {
  private LatLng latLng;

  public MarkerOptions position(LatLng latLng) {
    this.latLng = latLng;
    return this;
  }

  public LatLng getPosition() {
    return this.latLng;
  }
}
