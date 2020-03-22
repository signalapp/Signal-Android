package org.thoughtcrime.securesms.components.location;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.MarkerOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;

public class SignalMapView extends LinearLayout {

  private MapView   mapView;
  private ImageView imageView;
  private TextView  textView;

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

    this.mapView   = ViewUtil.findById(this, R.id.map_view);
    this.imageView = ViewUtil.findById(this, R.id.image_view);
    this.textView  = ViewUtil.findById(this, R.id.address_view);
  }

  public void setGoogleMapType(GoogleMap googleMap) {
    String mapType = TextSecurePreferences.getGoogleMapType(ApplicationDependencies.getApplication());
    if (mapType.equals("normal")) { googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL); }
    else if (mapType.equals("hybrid")) { googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID); }
    else if (mapType.equals("satellite")) { googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE); }
    else if (mapType.equals("terrain")) { googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN); }
    else if (mapType.equals("none")) { googleMap.setMapType(GoogleMap.MAP_TYPE_NONE); }
  }

  public ListenableFuture<Bitmap> display(final SignalPlace place) {
    final SettableFuture<Bitmap> future = new SettableFuture<>();

    this.mapView.onCreate(null);
    this.mapView.onResume();

    this.mapView.setVisibility(View.VISIBLE);
    this.imageView.setVisibility(View.GONE);

    this.mapView.getMapAsync(new OnMapReadyCallback() {
      @Override
      public void onMapReady(final GoogleMap googleMap) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLong(), 13));
        googleMap.addMarker(new MarkerOptions().position(place.getLatLong()));
        googleMap.setBuildingsEnabled(true);
        setGoogleMapType(googleMap);
        googleMap.getUiSettings().setAllGesturesEnabled(false);
        googleMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
          @Override
          public void onMapLoaded() {
            googleMap.snapshot(new GoogleMap.SnapshotReadyCallback() {
              @Override
              public void onSnapshotReady(Bitmap bitmap) {
                future.set(bitmap);
                imageView.setImageBitmap(bitmap);
                imageView.setVisibility(View.VISIBLE);
                mapView.setVisibility(View.GONE);
                mapView.onPause();
                mapView.onDestroy();
              }
            });
          }
        });
      }
    });

    this.textView.setText(place.getDescription());

    return future;
  }

}
