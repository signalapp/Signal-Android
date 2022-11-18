package org.thoughtcrime.securesms.sharing;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.mediasend.Media;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.List;

class ShareData {

  private final Optional<Uri>              uri;
  private final Optional<String>           mimeType;
  private final Optional<ArrayList<Media>> media;
  private final boolean                    external;
  private final boolean                    isMmsOrSmsSupported;

  static ShareData forIntentData(@NonNull Uri uri, @NonNull String mimeType, boolean external, boolean isMmsOrSmsSupported) {
    return new ShareData(Optional.of(uri), Optional.of(mimeType), Optional.absent(), external, isMmsOrSmsSupported);
  }

  static ShareData forPrimitiveTypes() {
    return new ShareData(Optional.absent(), Optional.absent(), Optional.absent(), true, true);
  }

  static ShareData forMedia(@NonNull List<Media> media, boolean isMmsOrSmsSupported) {
    return new ShareData(Optional.absent(), Optional.absent(), Optional.of(new ArrayList<>(media)), true, isMmsOrSmsSupported);
  }

  private ShareData(Optional<Uri> uri, Optional<String> mimeType, Optional<ArrayList<Media>> media, boolean external, boolean isMmsOrSmsSupported) {
    this.uri                 = uri;
    this.mimeType            = mimeType;
    this.media               = media;
    this.external            = external;
    this.isMmsOrSmsSupported = isMmsOrSmsSupported;
  }

  boolean isForIntent() {
    return uri.isPresent();
  }

  boolean isForPrimitive() {
    return !uri.isPresent() && !media.isPresent();
  }

  boolean isForMedia() {
    return media.isPresent();
  }

  public @NonNull Uri getUri() {
    return uri.get();
  }

  public @NonNull String getMimeType() {
    return mimeType.get();
  }

  public @NonNull ArrayList<Media> getMedia() {
    return media.get();
  }

  public boolean isExternal() {
    return external;
  }

  public boolean isMmsOrSmsSupported() {
    return isMmsOrSmsSupported;
  }
}
