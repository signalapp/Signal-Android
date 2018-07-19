package org.thoughtcrime.securesms.components.location;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.MarkerOptions;

import org.osmdroid.api.IMapController;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.IconOverlay;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.PlayServicesUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;

public class SignalMapView extends LinearLayout {

  private org.osmdroid.views.MapView osmdroidMapView;
  private MapView                    mapView;
  private ImageView                  imageView;
  private TextView                   textView;

  public SignalMapView(Context context) {
    this(context, null);
  }

  public SignalMapView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(context);
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public SignalMapView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize(context);
  }

  private void initialize(Context context) {
    setOrientation(LinearLayout.VERTICAL);
    LayoutInflater.from(context).inflate(R.layout.signal_map_view, this, true);

    this.osmdroidMapView = ViewUtil.findById(this, R.id.osmdroid_map_view);
    this.mapView         = ViewUtil.findById(this, R.id.map_view);
    this.imageView       = ViewUtil.findById(this, R.id.image_view);
    this.textView        = ViewUtil.findById(this, R.id.address_view);
  }

  public ListenableFuture<Bitmap> display(final SignalPlace place) {
    final SettableFuture<Bitmap> future = new SettableFuture<>();

    if (PlayServicesUtil.getPlayServicesStatus(getContext()) == PlayServicesUtil.PlayServicesStatus.SUCCESS) {
      initGoogleMap(future, place);
    } else {
      initOsmDroidMap(future, place);
    }

    this.textView.setText(place.getDescription(getContext()));
    return future;
  }

  private void initGoogleMap(SettableFuture<Bitmap> future, final SignalPlace place) {
    this.mapView.onCreate(null);
    this.mapView.onResume();

    this.mapView.setVisibility(View.VISIBLE);
    this.imageView.setVisibility(View.GONE);

    this.mapView.getMapAsync(googleMap -> {
      googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLong(), 13));
      googleMap.addMarker(new MarkerOptions().position(place.getLatLong()));
      googleMap.setBuildingsEnabled(true);
      googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
      googleMap.getUiSettings().setAllGesturesEnabled(false);
      googleMap.setOnMapLoadedCallback(() -> googleMap.snapshot(bitmap -> {
        future.set(bitmap);
        imageView.setImageBitmap(bitmap);
        imageView.setVisibility(View.VISIBLE);
        mapView.setVisibility(View.GONE);
        mapView.onPause();
        mapView.onDestroy();
      }));
    });
  }

  private void initOsmDroidMap(SettableFuture<Bitmap> future, SignalPlace place) {
    osmdroidMapView.onResume();
    osmdroidMapView.setVisibility(VISIBLE);
    imageView.setVisibility(View.GONE);

    IMapController mapController = osmdroidMapView.getController();
    GeoPoint geoPoint = new GeoPoint(place.getLatLong().latitude, place.getLatLong().longitude);
    mapController.setCenter(geoPoint);
    mapController.setZoom(18);

    Drawable drawable = getResources().getDrawable(R.drawable.ic_location_pin);
    IconOverlay overlay = new IconOverlay(geoPoint, drawable);

    osmdroidMapView.getOverlays().clear();
    osmdroidMapView.getOverlays().add(overlay);
    osmdroidMapView.invalidate();

    new Handler().postDelayed(() -> {
      osmdroidMapView.setDrawingCacheEnabled(true);
      osmdroidMapView.buildDrawingCache();
      Bitmap tempBitmap = osmdroidMapView.getDrawingCache();
      Bitmap bitmap = Bitmap.createBitmap(tempBitmap);

      future.set(bitmap);
      imageView.setImageBitmap(bitmap);
      imageView.setVisibility(View.VISIBLE);
      osmdroidMapView.setVisibility(View.GONE);
      osmdroidMapView.onPause();
    }, 250);
  }

}
