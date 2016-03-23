package org.thoughtcrime.securesms.attachments;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.InputStream;

public class UriAttachment extends Attachment {

  private final @NonNull Uri dataUri;
  private final @NonNull Uri thumbnailUri;

  public UriAttachment(@NonNull Uri uri, @NonNull String contentType, int transferState, long size) {
    this(uri, uri, contentType, transferState, size);
  }

  public UriAttachment(@NonNull Uri dataUri, @NonNull Uri thumbnailUri,
                       @NonNull String contentType, int transferState, long size)
  {
    super(contentType, transferState, size, null, null, null);
    this.dataUri      = dataUri;
    this.thumbnailUri = thumbnailUri;
  }

  @Override
  @NonNull
  public Uri getDataUri() {
    return dataUri;
  }

  @Override
  @NonNull
  public Uri getThumbnailUri() {
    return thumbnailUri;
  }

  @Override
  public boolean equals(Object other) {
    return other != null && other instanceof UriAttachment && ((UriAttachment) other).dataUri.equals(this.dataUri);
  }

  @Override
  public int hashCode() {
    return dataUri.hashCode();
  }
}
