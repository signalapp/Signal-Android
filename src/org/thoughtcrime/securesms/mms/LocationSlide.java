package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.whispersystems.libaxolotl.util.guava.Optional;

public class LocationSlide extends ImageSlide {

  @NonNull
  private final String description;

  public LocationSlide(@NonNull  Context context, @NonNull  Uri uri, long size, @NonNull String description)
  {
    super(context, uri, size);
    this.description = description;
  }

  @Override
  @NonNull
  public Optional<String> getBody() {
    return Optional.of(description);
  }
}
