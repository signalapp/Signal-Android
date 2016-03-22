package org.thoughtcrime.securesms.mms;

public class PushMediaConstraints extends MediaConstraints {
  private static final int MAX_IMAGE_DIMEN  = 1280;
  private static final int KB               = 1024;
  private static final int MB               = 1024 * KB;
  public  static final int MAX_MESSAGE_SIZE = 10000 * KB;

  @Override
  public int getImageMaxWidth() {
    return MAX_IMAGE_DIMEN;
  }

  @Override
  public int getImageMaxHeight() {
    return MAX_IMAGE_DIMEN;
  }

  @Override
  public int getImageMaxSize() {
    return 400 * KB;
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
