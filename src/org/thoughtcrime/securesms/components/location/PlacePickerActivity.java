/*
 * Copyright (C) 2018 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms.components.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import org.osmdroid.api.IMapController;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.IconOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.permissions.Permissions;

public class PlacePickerActivity extends PassphraseRequiredActionBarActivity
    implements MapEventsReceiver {

  private MapView mapView;
  private double  locationLat;
  private double  locationLong;

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.activity_place_picker);

    mapView = findViewById(R.id.map);
    mapView.setTileSource(TileSourceFactory.MAPNIK);
    mapView.setBuiltInZoomControls(true);
    mapView.setMultiTouchControls(true);

    requestLocation();
    initFab();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mapView.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mapView.onPause();
  }

  private void initFab() {
    FloatingActionButton fab = findViewById(R.id.fab_send_location);
    if (fab != null) {
      fab.setOnClickListener(v -> {
        EditText editTextName    = findViewById(R.id.edit_text_location_name);
        EditText editTextAddress = findViewById(R.id.edit_text_location_address);

        Intent data = new Intent();
        data.putExtra("lat", locationLat);
        data.putExtra("long", locationLong);

        if (editTextName != null) {
          data.putExtra("name", editTextName.getText().toString().trim());
        }

        if (editTextAddress != null) {
          data.putExtra("address", editTextAddress.getText().toString().trim());
        }

        setResult(RESULT_OK, data);
        finish();
      });
    }
  }

  @SuppressLint("MissingPermission")
  private void requestLocation() {
    Permissions.with(this)
        .request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .ifNecessary()
        .withPermanentDenialDialog(getString(R.string.AttachmentManager_signal_requires_location_information_in_order_to_attach_a_location))
        .onAllGranted(() -> {
          LocationManager manager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
          if (manager != null) {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
              manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, getMainLooper());
            } else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
              manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, getMainLooper());
            } else {
              Log.e("PlacePickerActivity", "Unable to request location update");
              finish();
            }
          }
        })
        .execute();
  }

  private void addPin(double locationLat, double locationLong) {
    this.locationLat  = locationLat;
    this.locationLong = locationLong;

    MapEventsOverlay eventOverlay = new MapEventsOverlay(getBaseContext(), this);
    GeoPoint geoPoint   = new GeoPoint(locationLat, locationLong);
    Drawable drawable   = getResources().getDrawable(R.drawable.ic_location_pin);
    IconOverlay overlay = new IconOverlay(geoPoint, drawable);

    mapView.getOverlays().clear();
    mapView.getOverlays().add(eventOverlay);
    mapView.getOverlays().add(overlay);
    mapView.invalidate();
  }

  private LocationListener locationListener = new LocationListener() {
    @Override
    public void onLocationChanged(Location location) {
      IMapController mapController = mapView.getController();
      GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
      mapController.setCenter(geoPoint);

      if (mapView.getVisibility() == View.GONE) {
        mapController.setZoom(18);
        mapView.setVisibility(View.VISIBLE);

        View fab = findViewById(R.id.fab_send_location);
        if (fab != null) {
          fab.setVisibility(View.VISIBLE);
        }
      }

      addPin(location.getLatitude(), location.getLongitude());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
      Log.d("LocationListener", String.format("Status changed (%s): %s", provider, status));
    }

    @Override
    public void onProviderEnabled(String provider) {
      Log.d("LocationListener", String.format("Provider enabled: %s", provider));
    }

    @Override
    public void onProviderDisabled(String provider) {
      Log.d("LocationListener", String.format("Provider disabled: %s", provider));
    }
  };

  @Override
  public boolean singleTapConfirmedHelper(GeoPoint p) {
    addPin(p.getLatitude(), p.getLongitude());
    return true;
  }

  @Override
  public boolean longPressHelper(GeoPoint p) {
    //We don't need to handle this event
    return false;
  }
}
