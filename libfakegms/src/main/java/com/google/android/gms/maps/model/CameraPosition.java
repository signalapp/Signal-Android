package com.google.android.gms.maps.model;

public final class CameraPosition {
  public LatLng target;
  public float zoom;

  public CameraPosition(LatLng target, float zoom) {
    this.target = target;
    this.zoom = zoom;
  }
}

