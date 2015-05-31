package org.thoughtcrime.securesms.mms;

public class PushMediaConstraints extends MediaConstraints {
  private static final int MAX_IMAGE_DIMEN  = 1280;
  private static final int KB               = 1024;
  private static final int MB               = 1024 * KB;

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
    return 420 * KB;
  }

  @Override
  public int getVideoMaxSize() {
    return MmsMediaConstraints.MAX_MESSAGE_SIZE;
  }

  @Override
  public int getAudioMaxSize() {
    return MmsMediaConstraints.MAX_MESSAGE_SIZE;
  }
}
