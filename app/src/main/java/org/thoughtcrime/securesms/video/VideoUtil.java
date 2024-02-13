package org.thoughtcrime.securesms.video;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Size;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants;

import java.util.concurrent.TimeUnit;

public final class VideoUtil {

  private VideoUtil() { }

  public static Size getVideoRecordingSize() {
    return isPortrait(screenSize())
           ? new Size(VideoConstants.VIDEO_SHORT_EDGE, VideoConstants.VIDEO_LONG_EDGE)
           : new Size(VideoConstants.VIDEO_LONG_EDGE, VideoConstants.VIDEO_SHORT_EDGE);
  }

  public static int getMaxVideoRecordDurationInSeconds(@NonNull Context context, @NonNull MediaConstraints mediaConstraints) {
    long allowedSize = mediaConstraints.getCompressedVideoMaxSize(context);
    int duration     = (int) Math.floor((float) allowedSize / VideoConstants.TOTAL_BYTES_PER_SECOND);

    return Math.min(duration, VideoConstants.VIDEO_MAX_RECORD_LENGTH_S);
  }

  private static Size screenSize() {
    DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
    return new Size(metrics.widthPixels, metrics.heightPixels);
  }

  private static boolean isPortrait(Size size) {
    return size.getWidth() < size.getHeight();
  }
}
