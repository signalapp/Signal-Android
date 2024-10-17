package org.thoughtcrime.securesms.profiles;


import android.content.Context;

import org.thoughtcrime.securesms.mms.MediaConstraints;

public class ProfileMediaConstraints extends MediaConstraints {
  @Override
  public int getImageMaxWidth(Context context) {
    return 640;
  }

  @Override
  public int getImageMaxHeight(Context context) {
    return 640;
  }

  @Override
  public int getImageMaxSize(Context context) {
    return 5 * 1024 * 1024;
  }

  @Override
  public int[] getImageDimensionTargets(Context context) {
    return new int[] { getImageMaxWidth(context) };
  }

  @Override
  public long getGifMaxSize(Context context) {
    return 0;
  }

  @Override
  public long getVideoMaxSize() {
    return 0;
  }

  @Override
  public long getAudioMaxSize(Context context) {
    return 0;
  }

  @Override
  public long getDocumentMaxSize(Context context) {
    return 0;
  }
}
