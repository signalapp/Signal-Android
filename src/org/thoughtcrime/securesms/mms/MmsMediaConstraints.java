package org.thoughtcrime.securesms.mms;

import android.content.Context;

import org.thoughtcrime.securesms.util.Util;

public class MmsMediaConstraints extends MediaConstraints {
  private static final int MAX_IMAGE_PIXELS_LOWMEM = 768 * 768 * 3 / 4;
  private static final int MAX_IMAGE_PIXELS        = 1024 * 1024 * 3 / 4;
  public  static final int MAX_MESSAGE_SIZE        = 280 * 1024;

  @Override
  public int getImageMaxPixels(Context context) {
    return Util.isLowMemory(context) ? MAX_IMAGE_PIXELS_LOWMEM : MAX_IMAGE_PIXELS;
  }

  @Override
  public int getImageMaxSize() {
    return MAX_MESSAGE_SIZE;
  }

  @Override
  public int getGifMaxSize() {
    return MAX_MESSAGE_SIZE;
  }

  @Override
  public int getVideoMaxSize() {
    return MAX_MESSAGE_SIZE;
  }

  @Override
  public int getAudioMaxSize() {
    return MAX_MESSAGE_SIZE;
  }
}
