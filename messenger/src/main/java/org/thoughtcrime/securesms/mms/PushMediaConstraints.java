package org.thoughtcrime.securesms.mms;

import android.content.Context;

import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.loki.api.fileserver.FileServerAPI;

public class PushMediaConstraints extends MediaConstraints {

  private static final int MAX_IMAGE_DIMEN_LOWMEM = 768;
  private static final int MAX_IMAGE_DIMEN        = 4096;

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
    return (int) (((double) FileServerAPI.Companion.getMaxFileSize()) / FileServerAPI.Companion.getFileSizeORMultiplier());
  }

  @Override
  public int getGifMaxSize(Context context) {
    return (int) (((double) FileServerAPI.Companion.getMaxFileSize()) / FileServerAPI.Companion.getFileSizeORMultiplier());
  }

  @Override
  public int getVideoMaxSize(Context context) {
    return (int) (((double) FileServerAPI.Companion.getMaxFileSize()) / FileServerAPI.Companion.getFileSizeORMultiplier());
  }

  @Override
  public int getAudioMaxSize(Context context) {
    return (int) (((double) FileServerAPI.Companion.getMaxFileSize()) / FileServerAPI.Companion.getFileSizeORMultiplier());
  }

  @Override
  public int getDocumentMaxSize(Context context) {
    return (int) (((double) FileServerAPI.Companion.getMaxFileSize()) / FileServerAPI.Companion.getFileSizeORMultiplier());
  }
}
