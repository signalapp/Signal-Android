package org.thoughtcrime.securesms.mms;

import android.content.Context;

import org.thoughtcrime.securesms.util.Util;

public class PushMediaConstraints extends MediaConstraints {
  private static final int MAX_IMAGE_PIXELS_LOWMEM = 768 * 768 * 3 / 4;
  private static final int MAX_IMAGE_PIXELS        = 1280 * 1280 * 3 / 4;
  private static final int KB                      = 1024;
  private static final int MB                      = 1024 * KB;

  @Override
  public int getImageMaxPixels(Context context) {
    return Util.isLowMemory(context) ? MAX_IMAGE_PIXELS_LOWMEM : MAX_IMAGE_PIXELS;
  }

  @Override
  public int getImageMaxSize() {
    return 420 * KB;
  }

  @Override
  public int getGifMaxSize() {
    return 5 * MB;
  }

  @Override
  public int getVideoMaxSize() {
    return 100 * MB;
  }

  @Override
  public int getAudioMaxSize() {
    return 100 * MB;
  }
}
