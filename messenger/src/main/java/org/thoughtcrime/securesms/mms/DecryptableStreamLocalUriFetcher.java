package org.thoughtcrime.securesms.mms;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import org.thoughtcrime.securesms.logging.Log;

import com.bumptech.glide.load.data.StreamLocalUriFetcher;

import org.thoughtcrime.securesms.util.MediaUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

class DecryptableStreamLocalUriFetcher extends StreamLocalUriFetcher {

  private static final String TAG = DecryptableStreamLocalUriFetcher.class.getSimpleName();

  private Context context;

  DecryptableStreamLocalUriFetcher(Context context, Uri uri) {
    super(context.getContentResolver(), uri);
    this.context      = context;
  }

  @Override
  protected InputStream loadResource(Uri uri, ContentResolver contentResolver) throws FileNotFoundException {
    if (MediaUtil.hasVideoThumbnail(uri)) {
      Bitmap thumbnail = MediaUtil.getVideoThumbnail(context, uri);

      if (thumbnail != null) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        return new ByteArrayInputStream(baos.toByteArray());
      }
    }

    try {
      return PartAuthority.getAttachmentStream(context, uri);
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      throw new FileNotFoundException("PartAuthority couldn't load Uri resource.");
    }
  }
}
