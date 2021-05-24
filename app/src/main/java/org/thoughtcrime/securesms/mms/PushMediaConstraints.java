package org.thoughtcrime.securesms.mms;

import android.content.Context;

import org.session.libsession.messaging.file_server.FileServerAPIV2;

public class PushMediaConstraints extends MediaConstraints {

  private static final int MAX_IMAGE_DIMEN_LOWMEM = 768;
  private static final int MAX_IMAGE_DIMEN        = 4096;

  @Override
  public int getImageMaxWidth(Context context) {
    return org.thoughtcrime.securesms.util.Util.isLowMemory(context) ? MAX_IMAGE_DIMEN_LOWMEM : MAX_IMAGE_DIMEN;
  }

  @Override
  public int getImageMaxHeight(Context context) {
    return getImageMaxWidth(context);
  }

  @Override
  public int getImageMaxSize(Context context) {
    return (int) (((double) FileServerAPIV2.maxFileSize) / FileServerAPIV2.fileSizeORMultiplier);
  }

  @Override
  public int getGifMaxSize(Context context) {
    return (int) (((double) FileServerAPIV2.maxFileSize) / FileServerAPIV2.fileSizeORMultiplier);
  }

  @Override
  public int getVideoMaxSize(Context context) {
    return (int) (((double) FileServerAPIV2.maxFileSize) / FileServerAPIV2.fileSizeORMultiplier);
  }

  @Override
  public int getAudioMaxSize(Context context) {
    return (int) (((double) FileServerAPIV2.maxFileSize) / FileServerAPIV2.fileSizeORMultiplier);
  }

  @Override
  public int getDocumentMaxSize(Context context) {
    return (int) (((double) FileServerAPIV2.maxFileSize) / FileServerAPIV2.fileSizeORMultiplier);
  }
}
