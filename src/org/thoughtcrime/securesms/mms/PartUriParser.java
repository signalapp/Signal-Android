package org.thoughtcrime.securesms.mms;

import android.content.ContentUris;
import android.net.Uri;

import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.util.Hex;

import java.io.IOException;

public class PartUriParser {

  private final Uri uri;

  public PartUriParser(Uri uri) {
    this.uri = uri;
  }

  public PartDatabase.PartId getPartId() {
    return new PartDatabase.PartId(getId(), getUniqueId());
  }

  private long getId() {
    return ContentUris.parseId(uri);
  }

  private long getUniqueId() {
    return Long.parseLong(uri.getPathSegments().get(1));
  }

}
