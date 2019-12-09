package org.thoughtcrime.securesms.insights;

final class InsightsData {
  private final boolean hasEnoughData;
  private final int     percentInsecure;

  InsightsData(boolean hasEnoughData, int percentInsecure) {
    this.hasEnoughData = hasEnoughData;
    this.percentInsecure = percentInsecure;
  }

  public boolean hasEnoughData() {
    return hasEnoughData;
  }

  public int getPercentInsecure() {
    return percentInsecure;
  }
}
