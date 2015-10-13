package org.thoughtcrime.securesms.attachments;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.io.IOException;
import java.io.InputStream;

public class UriAttachment extends Attachment {

  private final Uri dataUri;
  private final Uri thumbnailUri;

  public UriAttachment(Uri uri, String contentType, int transferState, long size) {
    this(uri, uri, contentType, transferState, size);
  }

  public UriAttachment(Uri dataUri, Uri thumbnailUri,
                       String contentType, int transferState, long size)
  {
    super(contentType, transferState, size, null, null, null);
    this.dataUri         = dataUri;
    this.thumbnailUri    = thumbnailUri;
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
}
