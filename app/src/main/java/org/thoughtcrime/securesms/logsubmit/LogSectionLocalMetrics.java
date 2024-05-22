package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.LocalMetricsDatabase;
import org.thoughtcrime.securesms.database.LocalMetricsDatabase.EventMetrics;
import org.thoughtcrime.securesms.database.LocalMetricsDatabase.SplitMetrics;
import org.thoughtcrime.securesms.dependencies.AppDependencies;

import java.util.List;

final class LogSectionLocalMetrics implements LogSection {
  @Override
  public @NonNull String getTitle() {
    return "LOCAL METRICS";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    List<EventMetrics> metrics = LocalMetricsDatabase.getInstance(AppDependencies.getApplication()).getMetrics();

    StringBuilder builder = new StringBuilder();

    for (EventMetrics metric : metrics) {
      builder.append(metric.getName()).append('\n')
             .append("  ").append("count: ").append(metric.getCount()).append('\n')
             .append("  ").append("p50: ").append(metric.getP50()).append('\n')
             .append("  ").append("p90: ").append(metric.getP90()).append('\n')
             .append("  ").append("p99: ").append(metric.getP99()).append('\n');

      for (SplitMetrics split : metric.getSplits()) {
        builder.append("    ").append(split.getName()).append('\n')
               .append("      ").append("p50: ").append(split.getP50()).append('\n')
               .append("      ").append("p90: ").append(split.getP90()).append('\n')
               .append("      ").append("p99: ").append(split.getP99()).append('\n');
      }
      builder.append("\n\n");
    }

    return builder;
  }
}
