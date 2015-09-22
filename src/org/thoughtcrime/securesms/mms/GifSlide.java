package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;

import ws.com.google.android.mms.pdu.PduPart;

public class GifSlide extends ImageSlide {
  public GifSlide(Context context, PduPart part) {
    super(context, part);
  }

  public GifSlide(Context context, Uri uri, long dataSize) throws IOException {
    super(context, uri, dataSize);
  }

  @Override public Uri getThumbnailUri() {
    return getPart().getDataUri();
  }
}
