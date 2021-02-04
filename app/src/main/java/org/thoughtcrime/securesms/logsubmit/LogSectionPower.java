package org.thoughtcrime.securesms.logsubmit;

import android.app.usage.UsageStatsManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.util.BucketInfo;

import java.util.concurrent.TimeUnit;

@RequiresApi(28)
public class LogSectionPower implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "POWER";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    final UsageStatsManager usageStatsManager = ContextCompat.getSystemService(context, UsageStatsManager.class);

    if (usageStatsManager == null) {
      return "UsageStatsManager not available";
    }

    BucketInfo info = BucketInfo.getInfo(usageStatsManager, TimeUnit.DAYS.toMillis(3));

    return new StringBuilder().append("Current bucket: ").append(BucketInfo.bucketToString(info.getCurrentBucket())).append('\n')
                              .append("Highest bucket: ").append(BucketInfo.bucketToString(info.getBestBucket())).append('\n')
                              .append("Lowest bucket : ").append(BucketInfo.bucketToString(info.getWorstBucket())).append("\n\n")
                              .append(info.getHistory());
  }
}
