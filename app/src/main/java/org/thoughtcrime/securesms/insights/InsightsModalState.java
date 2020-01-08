package org.thoughtcrime.securesms.insights;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class InsightsModalState {

  private final InsightsData       insightsData;
  private final InsightsUserAvatar userAvatar;

  private InsightsModalState(@NonNull Builder builder) {
    this.insightsData = builder.insightsData;
    this.userAvatar   = builder.userAvatar;
  }

  static @NonNull InsightsModalState.Builder builder() {
    return new InsightsModalState.Builder();
  }

  @NonNull InsightsModalState.Builder buildUpon() {
    return builder().withUserAvatar(userAvatar).withData(insightsData);
  }

  @Nullable InsightsUserAvatar getUserAvatar() {
    return userAvatar;
  }

  @Nullable InsightsData getData() {
    return insightsData;
  }

  static final class Builder {
    private InsightsData       insightsData;
    private InsightsUserAvatar userAvatar;

    private Builder() {
    }

    @NonNull Builder withData(@NonNull InsightsData insightsData) {
      this.insightsData = insightsData;
      return this;
    }

    @NonNull Builder withUserAvatar(@NonNull InsightsUserAvatar userAvatar) {
      this.userAvatar = userAvatar;
      return this;
    }

    @NonNull InsightsModalState build() {
      return new InsightsModalState(this);
    }
  }
}
