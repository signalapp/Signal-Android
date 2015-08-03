package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.bumptech.glide.Glide;
import com.bumptech.glide.gifdecoder.GifDecoder;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.io.IOException;
import java.io.InputStream;

import ws.com.google.android.mms.pdu.PduPart;

public abstract class MediaConstraints {
  private static final String TAG = MediaConstraints.class.getSimpleName();

  public static MediaConstraints MMS_CONSTRAINTS  = new MmsMediaConstraints();
  public static MediaConstraints PUSH_CONSTRAINTS = new PushMediaConstraints();

  public abstract int getImageMaxWidth(Context context);
  public abstract int getImageMaxHeight(Context context);
  public abstract int getImageMaxSize();

  public abstract int getGifMaxSize();

  public abstract int getVideoMaxSize();

  public abstract int getAudioMaxSize();

  public boolean isSatisfied(Context context, MasterSecret masterSecret, PduPart part) {
    try {
      return (MediaUtil.isGif(part)    && part.getDataSize() <= getGifMaxSize()   && isWithinBounds(context, masterSecret, part.getDataUri())) ||
             (MediaUtil.isImage(part)  && part.getDataSize() <= getImageMaxSize() && isWithinBounds(context, masterSecret, part.getDataUri())) ||
             (MediaUtil.isAudio(part)  && part.getDataSize() <= getAudioMaxSize()) ||
             (MediaUtil.isVideo(part)  && part.getDataSize() <= getVideoMaxSize()) ||
             (!MediaUtil.isImage(part) && !MediaUtil.isAudio(part) && !MediaUtil.isVideo(part));
    } catch (IOException ioe) {
      Log.w(TAG, "Failed to determine if media's constraints are satisfied.", ioe);
      return false;
    }
  }

  public boolean isWithinBounds(Context context, MasterSecret masterSecret, Uri uri) throws IOException {
    InputStream is = PartAuthority.getPartStream(context, masterSecret, uri);
    Pair<Integer, Integer> dimensions = BitmapUtil.getDimensions(is);
    return dimensions.first  > 0 && dimensions.first  <= getImageMaxWidth(context) &&
           dimensions.second > 0 && dimensions.second <= getImageMaxHeight(context);
  }

  public boolean canResize(PduPart part) {
    return part != null && MediaUtil.isImage(part) && !MediaUtil.isGif(part);
  }

  public byte[] getResizedMedia(Context context, MasterSecret masterSecret, PduPart part)
      throws IOException
  {
    if (!canResize(part) || part.getDataUri() == null) {
      throw new UnsupportedOperationException("Cannot resize this content type");
    }

    try {
      return BitmapUtil.createScaledBytes(context, masterSecret, part.getDataUri(),
                                          getImageMaxWidth(context),
                                          getImageMaxHeight(context),
                                          getImageMaxSize());
    } catch (BitmapDecodingException bde) {
      throw new IOException(bde);
    }
  }

}
