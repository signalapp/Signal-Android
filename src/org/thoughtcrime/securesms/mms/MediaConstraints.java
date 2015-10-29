package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

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
  public  static int CURRENT_MEDIA_SIZE = 280 * 1024;

  public abstract int getImageMaxWidth();
  public abstract int getImageMaxHeight();
  public abstract int getImageMaxSize();

  public abstract int getVideoMaxSize();

  public abstract int getAudioMaxSize();

  public boolean isSatisfied(Context context, MasterSecret masterSecret, PduPart part) {
    try {
      return (MediaUtil.isImage(part) && part.getDataSize() <= getImageMaxSize() && isWithinBounds(context, masterSecret, part.getDataUri())) ||
             (MediaUtil.isAudio(part) && part.getDataSize() <= getAudioMaxSize()) ||
             (MediaUtil.isVideo(part) && part.getDataSize() <= getVideoMaxSize()) ||
             (!MediaUtil.isImage(part) && !MediaUtil.isAudio(part) && !MediaUtil.isVideo(part));
    } catch (IOException ioe) {
      Log.w(TAG, "Failed to determine if media's constraints are satisfied.", ioe);
      return false;
    }
  }

  public boolean isWithinBounds(Context context, MasterSecret masterSecret, Uri uri) throws IOException {
    InputStream is = PartAuthority.getPartStream(context, masterSecret, uri);
    Pair<Integer, Integer> dimensions = BitmapUtil.getDimensions(is);
    return dimensions.first  > 0 && dimensions.first  <= getImageMaxWidth() &&
           dimensions.second > 0 && dimensions.second <= getImageMaxHeight();
  }

  public boolean canResize(PduPart part) {
    return part != null && MediaUtil.isImage(part);
  }

  public byte[] getResizedMedia(Context context, MasterSecret masterSecret, PduPart part)
      throws IOException
  {
    if (!canResize(part) || part.getDataUri() == null) {
      throw new UnsupportedOperationException("Cannot resize this content type");
    }

    try {
      return BitmapUtil.createScaledBytes(context, masterSecret, part.getDataUri(),
                                          getImageMaxWidth(),
                                          getImageMaxHeight(),
                                          getImageMaxSize());
    } catch (BitmapDecodingException bde) {
      //Decoed Stream was null at org.thoughtcrime.securesms.mms.MediaConstraints.getResizedMedia(MediaConstraints.java:69)
      throw new IOException(bde);
    }
  }

}
