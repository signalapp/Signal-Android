package com.google.android.gms.maps;

import com.google.android.gms.maps.model.LatLng;

public final class CameraUpdate {
  public final LatLng latLng;
  public final float zoom;

  public CameraUpdate(LatLng latLng, float zoom) {
   this.latLng = latLng;
   this.zoom = zoom;
  }
}
