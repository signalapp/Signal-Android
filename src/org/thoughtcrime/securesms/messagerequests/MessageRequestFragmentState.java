package org.thoughtcrime.securesms.messagerequests;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.List;

public class MessageRequestFragmentState {

  public enum MessageRequestState {
    LOADING,
    PENDING,
    BLOCKED,
    DELETED,
    ACCEPTED
  }

  public final @NonNull  MessageRequestState messageRequestState;
  public final @Nullable MessageRecord       messageRecord;
  public final @Nullable Recipient           recipient;
  public final @Nullable List<String>        groups;
  public final           int                 memberCount;


  public MessageRequestFragmentState(@NonNull MessageRequestState messageRequestState,
                                     @Nullable MessageRecord messageRecord,
                                     @Nullable Recipient recipient,
                                     @Nullable List<String> groups,
                                     int       memberCount)
  {
    this.messageRequestState = messageRequestState;
    this.messageRecord       = messageRecord;
    this.recipient           = recipient;
    this.groups              = groups;
    this.memberCount         = memberCount;
  }

  public @NonNull MessageRequestFragmentState updateMessageRequestState(@NonNull MessageRequestState messageRequestState) {
    return new MessageRequestFragmentState(messageRequestState,
                                           this.messageRecord,
                                           this.recipient,
                                           this.groups,
                                           this.memberCount);
  }

  public @NonNull MessageRequestFragmentState updateMessageRecord(@NonNull MessageRecord messageRecord) {
    return new MessageRequestFragmentState(this.messageRequestState,
                                           messageRecord,
                                           this.recipient,
                                           this.groups,
                                           this.memberCount);
  }

  public @NonNull MessageRequestFragmentState updateRecipient(@NonNull Recipient recipient) {
    return new MessageRequestFragmentState(this.messageRequestState,
                                           this.messageRecord,
                                           recipient,
                                           this.groups,
                                           this.memberCount);
  }

  public @NonNull MessageRequestFragmentState updateGroups(@NonNull List<String> groups) {
    return new MessageRequestFragmentState(this.messageRequestState,
                                           this.messageRecord,
                                           this.recipient,
                                           groups,
                                           this.memberCount);
  }

  public @NonNull MessageRequestFragmentState updateMemberCount(int memberCount) {
    return new MessageRequestFragmentState(this.messageRequestState,
                                           this.messageRecord,
                                           this.recipient,
                                           this.groups,
                                           memberCount);
  }

  @Override
  public @NonNull String toString() {
    return "MessageRequestFragmentState: [" +
           messageRequestState.name() + "] [" +
           messageRecord + "] [" +
           recipient + "] [" +
           groups + "] [" +
           memberCount + "]";
  }
}
