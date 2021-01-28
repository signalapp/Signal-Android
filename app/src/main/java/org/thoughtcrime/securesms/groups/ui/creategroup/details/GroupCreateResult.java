package org.thoughtcrime.securesms.groups.ui.creategroup.details;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.List;

abstract class GroupCreateResult {

  @WorkerThread
  static GroupCreateResult success(@NonNull GroupManager.GroupActionResult result) {
    return new GroupCreateResult.Success(result.getThreadId(), result.getGroupRecipient(), result.getAddedMemberCount(), Recipient.resolvedList(result.getInvitedMembers()));
  }

  static GroupCreateResult error(@NonNull GroupCreateResult.Error.Type errorType) {
    return new GroupCreateResult.Error(errorType);
  }

  private GroupCreateResult() {
  }

  static final class Success extends GroupCreateResult {
    private final long            threadId;
    private final Recipient       groupRecipient;
    private final int             addedMemberCount;
    private final List<Recipient> invitedMembers;

    private Success(long threadId,
                    @NonNull Recipient groupRecipient,
                    int addedMemberCount,
                    @NonNull List<Recipient> invitedMembers)
    {
      this.threadId         = threadId;
      this.groupRecipient   = groupRecipient;
      this.addedMemberCount = addedMemberCount;
      this.invitedMembers   = invitedMembers;
    }

    long getThreadId() {
      return threadId;
    }

    @NonNull Recipient getGroupRecipient() {
      return groupRecipient;
    }

    int getAddedMemberCount() {
      return addedMemberCount;
    }

    List<Recipient> getInvitedMembers() {
      return invitedMembers;
    }

    @Override
    void consume(@NonNull Consumer<Success> successConsumer,
                 @NonNull Consumer<Error> errorConsumer)
    {
      successConsumer.accept(this);
    }
  }

  static final class Error extends GroupCreateResult {
    private final Error.Type errorType;

    private Error(Error.Type errorType) {
      this.errorType = errorType;
    }

    @Override
    void consume(@NonNull Consumer<Success> successConsumer,
                 @NonNull Consumer<Error> errorConsumer)
    {
      errorConsumer.accept(this);
    }

    public Type getErrorType() {
      return errorType;
    }

    enum Type {
      ERROR_IO,
      ERROR_BUSY,
      ERROR_FAILED,
      ERROR_INVALID_NAME
    }
  }

  abstract void consume(@NonNull Consumer<Success> successConsumer,
                        @NonNull Consumer<Error> errorConsumer);

}
