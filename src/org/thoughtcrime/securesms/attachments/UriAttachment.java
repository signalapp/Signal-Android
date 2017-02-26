package org.thoughtcrime.securesms.attachments;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class UriAttachment extends Attachment {

  private final @NonNull  Uri dataUri;
  private final @Nullable Uri thumbnailUri;

  public UriAttachment(@NonNull Uri uri, @NonNull String contentType, int transferState, long size) {
    this(uri, uri, contentType, transferState, size);
  }

  public UriAttachment(@NonNull Uri dataUri, @Nullable Uri thumbnailUri,
                       @NonNull String contentType, int transferState, long size)
  {
    super(contentType, transferState, size, null, null, null, null);
    this.dataUri      = dataUri;
    this.thumbnailUri = thumbnailUri;
  }

  @Override
  @NonNull
  public Uri getDataUri() {
    return dataUri;
  }

  @Override
  @Nullable
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
