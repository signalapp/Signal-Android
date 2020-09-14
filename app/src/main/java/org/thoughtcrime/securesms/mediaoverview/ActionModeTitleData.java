package org.thoughtcrime.securesms.mediaoverview;

/** A data class that carries information to construct a string to display on an action bar. */
final class ActionModeTitleData {
  private int     mediaCount;
  private long    totalMediaSize;
  private boolean showFileSize;

  ActionModeTitleData(int mediaCount, long totalMediaSize, boolean showFileSize) {
    this.mediaCount     = mediaCount;
    this.totalMediaSize = totalMediaSize;
    this.showFileSize   = showFileSize;
  }

  public int getMediaCount() {
    return mediaCount;
  }

  public long getTotalMediaSize() {
    return totalMediaSize;
  }

  public boolean isShowFileSize() {
    return showFileSize;
  }
}
