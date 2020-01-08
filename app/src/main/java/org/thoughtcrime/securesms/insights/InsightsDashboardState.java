package org.thoughtcrime.securesms.insights;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Collections;
import java.util.List;

final class InsightsDashboardState {

  private final List<Recipient>    insecureRecipients;
  private final InsightsData       insightsData;
  private final InsightsUserAvatar userAvatar;

  private InsightsDashboardState(@NonNull Builder builder) {
    this.insecureRecipients = builder.insecureRecipients;
    this.insightsData       = builder.insightsData;
    this.userAvatar         = builder.userAvatar;
  }

  static @NonNull InsightsDashboardState.Builder builder() {
    return new InsightsDashboardState.Builder();
  }

  @NonNull InsightsDashboardState.Builder buildUpon() {
    return builder().withData(insightsData).withUserAvatar(userAvatar).withInsecureRecipients(insecureRecipients);
  }

  @NonNull List<Recipient> getInsecureRecipients() {
    return insecureRecipients;
  }

  @Nullable InsightsUserAvatar getUserAvatar() {
    return userAvatar;
  }

  @Nullable InsightsData getData() {
    return insightsData;
  }

  static final class Builder {
    private List<Recipient>    insecureRecipients = Collections.emptyList();
    private InsightsUserAvatar userAvatar;
    private InsightsData       insightsData;

    private Builder() {
    }

    @NonNull Builder withInsecureRecipients(@NonNull List<Recipient> insecureRecipients) {
      this.insecureRecipients = insecureRecipients;
      return this;
    }

    @NonNull Builder withData(@NonNull InsightsData insightsData) {
      this.insightsData = insightsData;
      return this;
    }

    @NonNull Builder withUserAvatar(@NonNull InsightsUserAvatar userAvatar) {
      this.userAvatar = userAvatar;
      return this;
    }

    @NonNull InsightsDashboardState build() {
      return new InsightsDashboardState(this);
    }
  }
}
