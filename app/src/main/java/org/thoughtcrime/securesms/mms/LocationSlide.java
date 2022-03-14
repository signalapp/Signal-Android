package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.components.location.SignalPlace;

import java.util.Optional;


public class LocationSlide extends ImageSlide {

  @NonNull
  private final SignalPlace place;

  public LocationSlide(@NonNull  Context context, @NonNull  Uri uri, long size, @NonNull SignalPlace place)
  {
    super(context, uri, size, 0, 0, null);
    this.place = place;
  }

  @Override
  @NonNull
  public Optional<String> getBody() {
    return Optional.of(place.getDescription());
  }

  @NonNull
  public SignalPlace getPlace() {
    return place;
  }

  @Override
  public boolean hasLocation() {
    return true;
  }

}
