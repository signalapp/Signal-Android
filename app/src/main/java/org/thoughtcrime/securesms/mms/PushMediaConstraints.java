package org.thoughtcrime.securesms.mms;

import android.content.Context;

import org.thoughtcrime.securesms.util.Util;

public class PushMediaConstraints extends MediaConstraints {

  private static final int MAX_IMAGE_DIMEN_LOWMEM = 768;
  private static final int MAX_IMAGE_DIMEN        = 1600;
  private static final int KB                     = 1024;
  private static final int MB                     = 1024 * KB;

  private static final int[] FALLBACKS        = { MAX_IMAGE_DIMEN, 1024, 768, 512 };
  private static final int[] FALLBACKS_LOWMEM = { MAX_IMAGE_DIMEN_LOWMEM, 512 };

  @Override
  public int getImageMaxWidth(Context context) {
    return Util.isLowMemory(context) ? MAX_IMAGE_DIMEN_LOWMEM : MAX_IMAGE_DIMEN;
  }

  @Override
  public int getImageMaxHeight(Context context) {
    return getImageMaxWidth(context);
  }

  @Override
  public int getImageMaxSize(Context context) {
    //noinspection PointlessArithmeticExpression
    return 1 * MB;
  }

  @Override
  public int[] getImageDimensionTargets(Context context) {
    return Util.isLowMemory(context) ? FALLBACKS_LOWMEM : FALLBACKS;
  }

  @Override
  public int getGifMaxSize(Context context) {
    return 25 * MB;
  }

  @Override
  public int getVideoMaxSize(Context context) {
    return 100 * MB;
  }

  @Override
  public int getUncompressedVideoMaxSize(Context context) {
    return isVideoTranscodeAvailable() ? 500 * MB
                                       : getVideoMaxSize(context);
  }

  @Override
  public int getCompressedVideoMaxSize(Context context) {
    return Util.isLowMemory(context) ? 30 * MB
                                     : 50 * MB;
  }

  @Override
  public int getAudioMaxSize(Context context) {
    return 100 * MB;
  }

  @Override
  public int getDocumentMaxSize(Context context) {
    return 100 * MB;
  }
}
