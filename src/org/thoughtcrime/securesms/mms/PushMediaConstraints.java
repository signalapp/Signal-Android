package org.thoughtcrime.securesms.mms;

import android.content.Context;

import org.thoughtcrime.securesms.util.Util;

public class PushMediaConstraints extends MediaConstraints {

  private static final int MAX_IMAGE_DIMEN_LOWMEM = 768;
  private static final int MAX_IMAGE_DIMEN        = 4096;
  private static final int KB                     = 1024;
  private static final int MB                     = 1024 * KB;

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
    return 6 * MB;
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
  public int getAudioMaxSize(Context context) {
    return 100 * MB;
  }

  @Override
  public int getDocumentMaxSize(Context context) {
    return 100 * MB;
  }
}
