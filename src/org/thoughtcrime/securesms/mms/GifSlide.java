package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore.Audio.Media;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.BitmapDecodingException;

import java.io.IOException;

import ws.com.google.android.mms.pdu.PduPart;

public class GifSlide extends ImageSlide {
  public GifSlide(Context context, MasterSecret masterSecret, PduPart part) {
    super(context, masterSecret, part);
  }

  public GifSlide(Context context, MasterSecret masterSecret, Uri uri) throws IOException {
    super(context, masterSecret, uri);
  }

  @Override public Uri getThumbnailUri() {
    return getPart().getDataUri();
  }
}
