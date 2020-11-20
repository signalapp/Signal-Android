package org.thoughtcrime.securesms.database.model;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Create a group call update message based on time and joined members.
 */
public class GroupCallUpdateMessageFactory implements UpdateDescription.StringFactory {
  private final Context                context;
  private final List<UUID>             joinedMembers;
  private final GroupCallUpdateDetails groupCallUpdateDetails;
  private final UUID                   selfUuid;

  public GroupCallUpdateMessageFactory(@NonNull Context context, @NonNull List<UUID> joinedMembers, @NonNull GroupCallUpdateDetails groupCallUpdateDetails) {
    this.context                = context;
    this.joinedMembers          = new ArrayList<>(joinedMembers);
    this.groupCallUpdateDetails = groupCallUpdateDetails;
    this.selfUuid               = TextSecurePreferences.getLocalUuid(context);

    boolean removed = this.joinedMembers.remove(selfUuid);
    if (removed) {
      this.joinedMembers.add(selfUuid);
    }
  }

  @Override
  public @NonNull String create() {
    String time = DateUtils.getTimeString(context, Locale.getDefault(), groupCallUpdateDetails.getStartedCallTimestamp());

    switch (joinedMembers.size()) {
      case 0:
        return context.getString(R.string.MessageRecord_group_call_s, time);
      case 1:
        if (joinedMembers.get(0).toString().equals(groupCallUpdateDetails.getStartedCallUuid())) {
          return context.getString(R.string.MessageRecord_s_started_a_group_call_s, describe(joinedMembers.get(0)), time);
        } else if (Objects.equals(joinedMembers.get(0), selfUuid)) {
          return context.getString(R.string.MessageRecord_you_are_in_the_group_call_s, describe(joinedMembers.get(0)), time);
        } else {
          return context.getString(R.string.MessageRecord_s_is_in_the_group_call_s, describe(joinedMembers.get(0)), time);
        }
      case 2:
        return context.getString(R.string.MessageRecord_s_and_s_are_in_the_group_call_s,
                                 describe(joinedMembers.get(0)),
                                 describe(joinedMembers.get(1)),
                                 time);
      default:
        int others = joinedMembers.size() - 2;
        return context.getResources().getQuantityString(R.plurals.MessageRecord_s_s_and_d_others_are_in_the_group_call_s,
                                                        others,
                                                        describe(joinedMembers.get(0)),
                                                        describe(joinedMembers.get(1)),
                                                        others,
                                                        time);
    }
  }

  private @NonNull String describe(@NonNull UUID uuid) {
    if (UuidUtil.UNKNOWN_UUID.equals(uuid)) {
      return context.getString(R.string.MessageRecord_unknown);
    }

    Recipient recipient = Recipient.resolved(RecipientId.from(uuid, null));

    if (recipient.isSelf()) {
      return context.getString(R.string.MessageRecord_you);
    } else {
      return recipient.getShortDisplayName(context);
    }
  }
}
