package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import org.thoughtcrime.securesms.logging.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class MediaConstraints {
  private static final String TAG = MediaConstraints.class.getSimpleName();

  public static MediaConstraints getPushMediaConstraints() {
    return new PushMediaConstraints();
  }

  public static MediaConstraints getMmsMediaConstraints(int subscriptionId) {
    return new MmsMediaConstraints(subscriptionId);
  }

  public abstract int getImageMaxWidth(Context context);
  public abstract int getImageMaxHeight(Context context);
  public abstract int getImageMaxSize(Context context);

  public abstract int getGifMaxSize(Context context);
  public abstract int getVideoMaxSize(Context context);
  public abstract int getAudioMaxSize(Context context);
  public abstract int getDocumentMaxSize(Context context);

  public boolean isSatisfied(@NonNull Context context, @NonNull Attachment attachment) {
    try {
      return (MediaUtil.isGif(attachment)    && attachment.getSize() <= getGifMaxSize(context)   && isWithinBounds(context, attachment.getDataUri())) ||
             (MediaUtil.isImage(attachment)  && attachment.getSize() <= getImageMaxSize(context) && isWithinBounds(context, attachment.getDataUri())) ||
             (MediaUtil.isAudio(attachment)  && attachment.getSize() <= getAudioMaxSize(context)) ||
             (MediaUtil.isVideo(attachment)  && attachment.getSize() <= getVideoMaxSize(context)) ||
             (MediaUtil.isFile(attachment) && attachment.getSize() <= getDocumentMaxSize(context));
    } catch (IOException ioe) {
      Log.w(TAG, "Failed to determine if media's constraints are satisfied.", ioe);
      return false;
    }
  }

  private boolean isWithinBounds(Context context, Uri uri) throws IOException {
    try {
      InputStream is = PartAuthority.getAttachmentStream(context, uri);
      Pair<Integer, Integer> dimensions = BitmapUtil.getDimensions(is);
      return dimensions.first  > 0 && dimensions.first  <= getImageMaxWidth(context) &&
             dimensions.second > 0 && dimensions.second <= getImageMaxHeight(context);
    } catch (BitmapDecodingException e) {
      throw new IOException(e);
    }
  }

  public boolean canResize(@Nullable Attachment attachment) {
    return attachment != null && MediaUtil.isImage(attachment) && !MediaUtil.isGif(attachment);
  }

  public MediaStream getResizedMedia(@NonNull Context context, @NonNull Attachment attachment)
      throws IOException
  {
    if (!canResize(attachment)) {
      throw new UnsupportedOperationException("Cannot resize this content type");
    }

    try {
      // XXX - This is loading everything into memory! We want the send path to be stream-like.
      BitmapUtil.ScaleResult scaleResult = BitmapUtil.createScaledBytes(context, new DecryptableUri(attachment.getDataUri()), this);
      return new MediaStream(new ByteArrayInputStream(scaleResult.getBitmap()), MediaUtil.IMAGE_JPEG, scaleResult.getWidth(), scaleResult.getHeight());
    } catch (BitmapDecodingException e) {
      throw new IOException(e);
    }
  }
}
