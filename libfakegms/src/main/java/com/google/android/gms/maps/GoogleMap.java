package com.google.android.gms.maps;

import android.graphics.Bitmap;

import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;

public class GoogleMap {
  public static final int MAP_TYPE_NONE = 0;
  public static final int MAP_TYPE_NORMAL = 1;
  public static final int MAP_TYPE_SATELLITE = 2;
  public static final int MAP_TYPE_TERRAIN = 3;
  public static final int MAP_TYPE_HYBRID = 4;

  private final MapView     mapView;
  private final UiSettings  uiSettings  = new UiSettings();
  private final MapListener mapListener = new MapListener() {

    private boolean moving = false;

    private final Runnable idleDetector = () -> {
      moving = false;
      onCameraIdle();
    };

    @Override
    public boolean onScroll(ScrollEvent event) {
      if (moving) return true;

      onCameraMoveStarted();
      moving = true;
      mapView.getHandler().removeCallbacks(idleDetector);
      mapView.getHandler().postDelayed(idleDetector, 1000);

      return true;
    }

    @Override
    public boolean onZoom(ZoomEvent event) {
      return false;
    }
  };

  private CameraPosition              cameraPosition;
  private OnCameraIdleListener        onCameraIdleListener;
  private OnCameraMoveStartedListener onCameraMoveStartedListener;

  public GoogleMap(MapView mapView) {
    this.mapView = mapView;
    this.mapView.addMapListener(mapListener);
  }

  public void setOnCameraMoveStartedListener(OnCameraMoveStartedListener onCameraMoveStartedListener) {
    this.onCameraMoveStartedListener = onCameraMoveStartedListener;
  }

  public void setOnCameraIdleListener(OnCameraIdleListener onCameraIdleListener) {
    this.onCameraIdleListener = onCameraIdleListener;
  }

  public CameraPosition getCameraPosition() {
    return cameraPosition;
  }

  public void moveCamera(CameraUpdate update) {
    // Zoom in a little bit because OSM Droid's zoom level is slightly wider than Google Maps'
    float zoom = update.zoom + 2.0f;
    mapView.setMapPosition(new CameraPosition(update.latLng, zoom));
  }

  public Marker addMarker(MarkerOptions options) {
    mapView.addMarker(options);
    return new Marker();
  }

  public void setMyLocationEnabled(boolean myLocationEnabled) {
    mapView.setMyLocationEnabled(myLocationEnabled);
  }

  public void setBuildingsEnabled(boolean isBuildingsEnabled) { }

  public void setMapType (int type) { }

  public UiSettings getUiSettings() {
    return uiSettings;
  }

  public void setOnMapLoadedCallback (OnMapLoadedCallback callback) {
    mapView.setOnMapLoadedCallback(callback);
  }

  public void snapshot(SnapshotReadyCallback callback) {
    Bitmap bitmap = mapView.snapshot();
    callback.onSnapshotReady(bitmap);
  }

  private void onCameraIdle() {
    if (onCameraIdleListener != null) {
      onCameraIdleListener.onCameraIdle();
    }
  }

  private void onCameraMoveStarted() {
    IGeoPoint geoPoint = mapView.getMapCenter();
    LatLng center      = new LatLng(geoPoint.getLatitude(), geoPoint.getLongitude());
    cameraPosition     = new CameraPosition(center, (float) mapView.getZoomLevelDouble());

    if (onCameraMoveStartedListener != null) {
      onCameraMoveStartedListener.onCameraMoveStarted(0);
    }
  }

  public interface OnCameraMoveStartedListener {
    void onCameraMoveStarted(int reason);
  }

  public interface OnCameraIdleListener {
    void onCameraIdle();
  }

  public interface OnMapLoadedCallback {
    void onMapLoaded();
  }

  public interface SnapshotReadyCallback {
    void onSnapshotReady(Bitmap snapshot);
  }
}
