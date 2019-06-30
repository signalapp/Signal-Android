package org.thoughtcrime.securesms.util;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.Date;

@RequiresApi(28)
public final class BucketInfo {

  /**
   * UsageStatsManager.STANDBY_BUCKET_EXEMPTED: is a Hidden API
   */
  public static final int STANDBY_BUCKET_EXEMPTED = 5;

  private final int          currentBucket;
  private final int          worstBucket;
  private final int          bestBucket;
  private final CharSequence history;

  private BucketInfo(int currentBucket, int worstBucket, int bestBucket, CharSequence history) {
    this.currentBucket = currentBucket;
    this.worstBucket   = worstBucket;
    this.bestBucket    = bestBucket;
    this.history       = history;
  }

  public static @NonNull BucketInfo getInfo(@NonNull UsageStatsManager usageStatsManager, long overLastDurationMs) {
    StringBuilder stringBuilder = new StringBuilder();

    int currentBucket = usageStatsManager.getAppStandbyBucket();
    int worseBucket   = currentBucket;
    int bestBucket    = currentBucket;

    long              now           = System.currentTimeMillis();
    UsageEvents.Event event         = new UsageEvents.Event();
    UsageEvents       usageEvents   = usageStatsManager.queryEventsForSelf(now - overLastDurationMs, now);

    while (usageEvents.hasNextEvent()) {
      usageEvents.getNextEvent(event);

      if (event.getEventType() == UsageEvents.Event.STANDBY_BUCKET_CHANGED) {
        int appStandbyBucket = event.getAppStandbyBucket();

        stringBuilder.append(new Date(event.getTimeStamp()))
                     .append(": ")
                     .append("Bucket Change: ")
                     .append(bucketToString(appStandbyBucket))
                     .append("\n");

        if (appStandbyBucket > worseBucket) {
          worseBucket = appStandbyBucket;
        }
        if (appStandbyBucket < bestBucket) {
          bestBucket = appStandbyBucket;
        }
      }
    }

    return new BucketInfo(currentBucket, worseBucket, bestBucket, stringBuilder);
  }

  /**
   * Not localized, for logs and debug only.
   */
  public static String bucketToString(int bucket) {
    switch (bucket) {
      case UsageStatsManager.STANDBY_BUCKET_ACTIVE:      return "Active";
      case UsageStatsManager.STANDBY_BUCKET_FREQUENT:    return "Frequent";
      case UsageStatsManager.STANDBY_BUCKET_WORKING_SET: return "Working Set";
      case UsageStatsManager.STANDBY_BUCKET_RARE:        return "Rare";
      case                   STANDBY_BUCKET_EXEMPTED:    return "Exempted";
      default:                                           return "Unknown " + bucket;
    }
  }

  public int getBestBucket() {
    return bestBucket;
  }

  public int getWorstBucket() {
    return worstBucket;
  }

  public int getCurrentBucket() {
    return currentBucket;
  }

  public CharSequence getHistory() {
    return history;
  }
}
