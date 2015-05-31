package org.thoughtcrime.securesms.mms;

public class MmsMediaConstraints extends MediaConstraints {
  private static final int MAX_IMAGE_DIMEN  = 1024;
  public  static final int MAX_MESSAGE_SIZE = 280 * 1024;

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
