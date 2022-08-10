package org.thoughtcrime.securesms.backup;

public class BackupEvent {
  public enum Type {
    PROGRESS,
    PROGRESS_VERIFYING,
    FINISHED
  }

  private final Type type;
  private final long count;
  private final long estimatedTotalCount;

  public BackupEvent(Type type, long count, long estimatedTotalCount) {
    this.type                = type;
    this.count               = count;
    this.estimatedTotalCount = estimatedTotalCount;
  }

  public Type getType() {
    return type;
  }

  public long getCount() {
    return count;
  }

  public long getEstimatedTotalCount() {
    return estimatedTotalCount;
  }

  public double getCompletionPercentage() {
    if (estimatedTotalCount == 0) {
      return 0;
    }

    return Math.min(99.9f, (double) count * 100L / (double) estimatedTotalCount);
  }
}
