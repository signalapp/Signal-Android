package org.thoughtcrime.securesms.database.model;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DateUtils;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Create a group call update message based on time and joined members.
 */
public class GroupCallUpdateMessageFactory implements UpdateDescription.SpannableFactory {
  private final Context                context;
  private final List<ServiceId>        joinedMembers;
  private final boolean                withTime;
  private final GroupCallUpdateDetails groupCallUpdateDetails;
  private final ACI                    selfAci;

  public GroupCallUpdateMessageFactory(@NonNull Context context,
                                       @NonNull List<ServiceId> joinedMembers,
                                       boolean withTime,
                                       @NonNull GroupCallUpdateDetails groupCallUpdateDetails)
  {
    this.context                = context;
    this.joinedMembers          = new ArrayList<>(joinedMembers);
    this.withTime               = withTime;
    this.groupCallUpdateDetails = groupCallUpdateDetails;
    this.selfAci                = SignalStore.account().requireAci();

    boolean removed = this.joinedMembers.remove(selfAci);
    if (removed) {
      this.joinedMembers.add(selfAci);
    }
  }

  @Override
  public @NonNull Spannable create() {
    return new SpannableString(createString());
  }

  private @NonNull String createString() {
    long    endedTimestamp  = groupCallUpdateDetails.endedCallTimestamp;
    boolean isWithinTimeout = GroupCallUpdateDetailsUtil.checkCallEndedRecently(groupCallUpdateDetails);
    String  time            = DateUtils.getTimeString(context, Locale.getDefault(), groupCallUpdateDetails.startedCallTimestamp);
    boolean isOutgoing      = Objects.equals(selfAci.toString(), groupCallUpdateDetails.startedCallUuid);

    switch (joinedMembers.size()) {
      case 0:
        if (isWithinTimeout) {
          return withTime ? context.getString(R.string.MessageRecord__the_video_call_has_ended_s, time)
                          : context.getString(R.string.MessageRecord__the_video_call_has_ended);
        } else if (endedTimestamp == 0 || groupCallUpdateDetails.localUserJoined) {
          if (isOutgoing) {
            return withTime ? context.getString(R.string.MessageRecord__outgoing_video_call_s, time)
                            : context.getString(R.string.MessageRecord__outgoing_video_call);
          } else {
            return withTime ? context.getString(R.string.MessageRecord__incoming_video_call_s, time)
                            : context.getString(R.string.MessageRecord__incoming_video_call);
          }
        } else {
          return withTime ? context.getString(R.string.MessageRecord__missed_video_call_s, time)
                          : context.getString(R.string.MessageRecord__missed_video_call);
        }
      case 1:
        if (joinedMembers.get(0).toString().equals(groupCallUpdateDetails.startedCallUuid)) {
          if (Objects.equals(joinedMembers.get(0), selfAci)) {
            return withTime ? context.getString(R.string.MessageRecord__you_started_a_video_call_s, time)
                            : context.getString(R.string.MessageRecord__you_started_a_video_call);
          } else {
            return withTime ? context.getString(R.string.MessageRecord__s_started_a_video_call_s, describe(joinedMembers.get(0)), time)
                            : context.getString(R.string.MessageRecord__s_started_a_video_call, describe(joinedMembers.get(0)));
          }
        } else if (Objects.equals(joinedMembers.get(0), selfAci)) {
          return withTime ? context.getString(R.string.MessageRecord_you_are_in_the_call_s1, time)
                          : context.getString(R.string.MessageRecord_you_are_in_the_call);
        } else {
          return withTime ? context.getString(R.string.MessageRecord_s_is_in_the_call_s, describe(joinedMembers.get(0)), time)
                          : context.getString(R.string.MessageRecord_s_is_in_the_call, describe(joinedMembers.get(0)));
        }
      case 2:
        return withTime ? context.getString(R.string.MessageRecord_s_and_s_are_in_the_call_s1,
                                            describe(joinedMembers.get(0)),
                                            describe(joinedMembers.get(1)),
                                            time)
                        : context.getString(R.string.MessageRecord_s_and_s_are_in_the_call,
                                            describe(joinedMembers.get(0)),
                                            describe(joinedMembers.get(1)));
      default:
        int others = joinedMembers.size() - 2;
        return withTime ? context.getResources().getQuantityString(R.plurals.MessageRecord_s_s_and_d_others_are_in_the_call_s,
                                                                   others,
                                                                   describe(joinedMembers.get(0)),
                                                                   describe(joinedMembers.get(1)),
                                                                   others,
                                                                   time)
                        : context.getResources().getQuantityString(R.plurals.MessageRecord_s_s_and_d_others_are_in_the_call,
                                                                   others,
                                                                   describe(joinedMembers.get(0)),
                                                                   describe(joinedMembers.get(1)),
                                                                   others);
    }
  }

  private @NonNull String describe(@NonNull ServiceId serviceId) {
    if (serviceId.isUnknown()) {
      return context.getString(R.string.MessageRecord_unknown);
    }

    Recipient recipient = Recipient.resolved(RecipientId.from(serviceId));

    if (recipient.isSelf()) {
      return context.getString(R.string.MessageRecord_you);
    } else {
      return recipient.getShortDisplayName(context);
    }
  }
}
