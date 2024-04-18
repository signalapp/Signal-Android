package org.thoughtcrime.securesms.profiles.spoofing;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

public class ReviewRecipient {
  private final Recipient            recipient;
  private final ProfileChangeDetails profileChangeDetails;

  public ReviewRecipient(@NonNull Recipient recipient) {
    this(recipient, null);
  }

  public ReviewRecipient(@NonNull Recipient recipient, @Nullable ProfileChangeDetails profileChangeDetails) {
    this.recipient            = recipient;
    this.profileChangeDetails = profileChangeDetails;
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  public @Nullable ProfileChangeDetails getProfileChangeDetails() {
    return profileChangeDetails;
  }

  public static class Comparator implements java.util.Comparator<ReviewRecipient> {

    private final Context     context;
    private final RecipientId alwaysFirstId;

    public Comparator(@NonNull Context context, @Nullable RecipientId alwaysFirstId) {
      this.context       = context;
      this.alwaysFirstId = alwaysFirstId;
    }

    @Override
    public int compare(ReviewRecipient recipient1, ReviewRecipient recipient2) {
      int weight1 = recipient1.getRecipient().getId().equals(alwaysFirstId) ? -100 : 0;
      int weight2 = recipient2.getRecipient().getId().equals(alwaysFirstId) ? -100 : 0;

      if (recipient1.getProfileChangeDetails() != null && recipient1.getProfileChangeDetails().profileNameChange != null) {
        weight1--;
      }

      if (recipient2.getProfileChangeDetails() != null && recipient2.getProfileChangeDetails().profileNameChange != null) {
        weight2--;
      }

      if (recipient1.getRecipient().isSystemContact()) {
        weight1++;
      }

      if (recipient2.getRecipient().isSystemContact()) {
        weight1++;
      }

      if (weight1 == weight2) {
        return recipient1.getRecipient()
                         .getDisplayName(context)
                         .compareTo(recipient2.getRecipient()
                                              .getDisplayName(context));
      } else {
        return Integer.compare(weight1, weight2);
      }
    }
  }
}
