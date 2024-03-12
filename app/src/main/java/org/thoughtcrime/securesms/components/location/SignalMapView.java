package org.thoughtcrime.securesms.components.location;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.signal.core.util.concurrent.ListenableFuture;
import org.signal.core.util.concurrent.SettableFuture;
import org.thoughtcrime.securesms.R;

import java.util.concurrent.ExecutionException;

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

  public SignalMapView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize(context);
  }

  private void initialize(Context context) {
    setOrientation(LinearLayout.VERTICAL);
    LayoutInflater.from(context).inflate(R.layout.signal_map_view, this, true);

    this.mapView   = findViewById(R.id.map_view);
    this.imageView = findViewById(R.id.image_view);
    this.textView  = findViewById(R.id.address_view);
  }

  public ListenableFuture<Bitmap> display(final SignalPlace place) {
    final SettableFuture<Bitmap> future = new SettableFuture<>();

    this.imageView.setVisibility(View.GONE);
    this.textView.setText(place.getDescription());
    snapshot(place, mapView).addListener(new ListenableFuture.Listener<Bitmap>() {
      @Override
      public void onSuccess(Bitmap result) {
        future.set(result);
        imageView.setImageBitmap(result);
        imageView.setVisibility(View.VISIBLE);
      }

      @Override
      public void onFailure(ExecutionException e) {
        future.setException(e);
      }
    });

    return future;
  }

  public static ListenableFuture<Bitmap> snapshot(final LatLng place, @NonNull final MapView mapView) {
    final SettableFuture<Bitmap> future = new SettableFuture<>();
    mapView.onCreate(null);
    mapView.onStart();
    mapView.onResume();

    mapView.setVisibility(View.VISIBLE);

    mapView.getMapAsync(googleMap -> {
      googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place, 13));
      googleMap.addMarker(new MarkerOptions().position(place));
      googleMap.setBuildingsEnabled(true);
      googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
      googleMap.getUiSettings().setAllGesturesEnabled(false);
      googleMap.setOnMapLoadedCallback(() -> googleMap.snapshot(bitmap -> {
        future.set(bitmap);
        mapView.setVisibility(View.GONE);
        mapView.onPause();
        mapView.onStop();
        mapView.onDestroy();
      }));
    });

    return future;
  }
  public static ListenableFuture<Bitmap> snapshot(final SignalPlace place, @NonNull final MapView mapView) {
    return snapshot(place.getLatLong(), mapView);
  }

}
