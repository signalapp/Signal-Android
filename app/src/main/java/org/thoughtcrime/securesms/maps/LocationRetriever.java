package org.thoughtcrime.securesms.maps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.ServiceUtil;

/**
 * A lifecycle-safe way to retrieve a single location update. If a cached location is available,
 * we'll use that. Otherwise we'll listen for one.
 */
class LocationRetriever implements DefaultLifecycleObserver, LocationListener {

  private static final String TAG = Log.tag(LocationRetriever.class);

  private final Context         context;
  private final LocationManager locationManager;
  private final SuccessListener successListener;
  private final FailureListener failureListener;

  LocationRetriever(@NonNull Context context, @NonNull LifecycleOwner lifecycleOwner, @NonNull SuccessListener successListener, @NonNull FailureListener failureListener) {
    this.context         = context;
    this.locationManager = ServiceUtil.getLocationManager(context);
    this.successListener = successListener;
    this.failureListener = failureListener;

    lifecycleOwner.getLifecycle().addObserver(this);
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
    {
      Log.w(TAG, "No location permission!");
      failureListener.onFailure();
    }

    LocationProvider provider = locationManager.getProvider(LocationManager.GPS_PROVIDER);

    if (provider == null) {
      Log.w(TAG, "GPS provider is null. Trying network provider.");
      provider = locationManager.getProvider(LocationManager.NETWORK_PROVIDER);
    }

    if (provider == null) {
      Log.w(TAG, "Network provider is null. Unable to retrieve location.");
      failureListener.onFailure();
      return;
    }

    Location lastKnown = locationManager.getLastKnownLocation(provider.getName());

    if (lastKnown != null) {
      Log.i(TAG, "Using last known location.");
      successListener.onSuccess(lastKnown);
    } else {
      Log.i(TAG, "No last known location. Requesting a single update.");
      locationManager.requestSingleUpdate(provider.getName(), this, null);
    }
  }

  @SuppressLint("MissingPermission")
  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    Log.i(TAG, "Removing any possible location listeners.");
    locationManager.removeUpdates(this);
  }

  @Override
  public void onLocationChanged(@Nullable Location location) {
    if (location != null) {
      Log.w(TAG, "[onLocationChanged] Successfully retrieved location.");
      successListener.onSuccess(location);
    } else {
      Log.w(TAG, "[onLocationChanged] Null location.");
      failureListener.onFailure();
    }
  }

  @Override
  public void onStatusChanged(@NonNull String provider, int status, @Nullable Bundle extras) {
    Log.i(TAG, "[onStatusChanged] Provider: " + provider + " Status: " + status);
  }

  @Override
  public void onProviderEnabled(@NonNull String provider) {
    Log.i(TAG, "[onProviderEnabled] Provider: " + provider);
  }

  @Override
  public void onProviderDisabled(@NonNull String provider) {
    Log.i(TAG, "[onProviderDisabled] Provider: " + provider);
  }

  interface SuccessListener {
    void onSuccess(@NonNull Location location);
  }

  interface FailureListener {
    void onFailure();
  }
}
