package org.thoughtcrime.securesms.mms;

import android.content.Context;

import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.w3c.dom.Text;

public class MmsMediaConstraints extends MediaConstraints {
  private static final int MAX_IMAGE_DIMEN_LOWMEM = 768;
  private static final int MAX_IMAGE_DIMEN        = 1024;
  public  static final int SIZE_CONSTANT          = 1024;
  //public  static final int MAX_MESSAGE_SIZE       = 280 * 1024;

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
    return TextSecurePreferences.getMMSMaxSize(context) * SIZE_CONSTANT;
    //return MAX_MESSAGE_SIZE;
  }

  @Override
  public int getGifMaxSize(Context context) {
    return TextSecurePreferences.getMMSMaxSize(context) * SIZE_CONSTANT;
    //return MAX_MESSAGE_SIZE;
  }

  @Override
  public int getVideoMaxSize(Context context) {
    return TextSecurePreferences.getMMSMaxSize(context) * SIZE_CONSTANT;
    //return MAX_MESSAGE_SIZE;
  }

  @Override
  public int getAudioMaxSize(Context context) {
    return TextSecurePreferences.getMMSMaxSize(context) * SIZE_CONSTANT;
    //return MAX_MESSAGE_SIZE;
  }
}
