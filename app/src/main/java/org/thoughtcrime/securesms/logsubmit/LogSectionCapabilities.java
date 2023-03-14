package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.AppCapabilities;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.account.AccountAttributes;

public final class LogSectionCapabilities implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "CAPABILITIES";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    if (!SignalStore.account().isRegistered()) {
      return "Unregistered";
    }

    if (SignalStore.account().getE164() == null || SignalStore.account().getAci() == null) {
      return "Self not yet available!";
    }

    Recipient self = Recipient.self();

    AccountAttributes.Capabilities localCapabilities  = AppCapabilities.getCapabilities(false);
    RecipientRecord.Capabilities   globalCapabilities = SignalDatabase.recipients().getCapabilities(self.getId());

    StringBuilder builder = new StringBuilder().append("-- Local").append("\n")
                                               .append("GV2                : ").append(localCapabilities.getGv2()).append("\n")
                                               .append("GV1 Migration      : ").append(localCapabilities.getGv1Migration()).append("\n")
                                               .append("Sender Key         : ").append(localCapabilities.getSenderKey()).append("\n")
                                               .append("Announcement Groups: ").append(localCapabilities.getAnnouncementGroup()).append("\n")
                                               .append("Change Number      : ").append(localCapabilities.getChangeNumber()).append("\n")
                                               .append("Stories            : ").append(localCapabilities.getStories()).append("\n")
                                               .append("Gift Badges        : ").append(localCapabilities.getGiftBadges()).append("\n")
                                               .append("\n")
                                               .append("-- Global").append("\n");

    if (globalCapabilities != null) {
      builder.append("GV1 Migration      : ").append(globalCapabilities.getGroupsV1MigrationCapability()).append("\n")
             .append("Sender Key         : ").append(globalCapabilities.getSenderKeyCapability()).append("\n")
             .append("Announcement Groups: ").append(globalCapabilities.getAnnouncementGroupCapability()).append("\n")
             .append("Change Number      : ").append(globalCapabilities.getChangeNumberCapability()).append("\n")
             .append("Stories            : ").append(globalCapabilities.getStoriesCapability()).append("\n")
             .append("Gift Badges        : ").append(globalCapabilities.getGiftBadgesCapability()).append("\n");
    } else {
      builder.append("Self not found!");
    }

    return builder;
  }
}
