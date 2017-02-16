package org.thoughtcrime.securesms.mms;

import android.net.Uri;

public class PersistentBlobUriParser {

  private final Uri uri;

  public PersistentBlobUriParser(Uri uri) {
    this.uri = uri;
  }

  public long getId() {
    return Long.parseLong(uri.getPathSegments().get(3));
  }

  public long getUniqueId() {
    return Long.parseLong(uri.getPathSegments().get(2));
  }
}
