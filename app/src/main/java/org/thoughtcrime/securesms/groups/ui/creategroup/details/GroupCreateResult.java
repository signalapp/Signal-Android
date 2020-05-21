package org.thoughtcrime.securesms.groups.ui.creategroup.details;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.recipients.Recipient;

abstract class GroupCreateResult {

  static GroupCreateResult success(@NonNull GroupManager.GroupActionResult result) {
    return new GroupCreateResult.Success(result.getThreadId(), result.getGroupRecipient());
  }

  static GroupCreateResult error(@NonNull GroupCreateResult.Error.Type errorType) {
    return new GroupCreateResult.Error(errorType);
  }

  private GroupCreateResult() {
  }

  static final class Success extends GroupCreateResult {
    private final long      threadId;
    private final Recipient groupRecipient;

    private Success(long threadId, @NonNull Recipient groupRecipient) {
      this.threadId       = threadId;
      this.groupRecipient = groupRecipient;
    }

    long getThreadId() {
      return threadId;
    }

    @NonNull Recipient getGroupRecipient() {
      return groupRecipient;
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
      ERROR_INVALID_NAME,
      ERROR_INVALID_MEMBER_COUNT
    }
  }

  abstract void consume(@NonNull Consumer<Success> successConsumer,
                        @NonNull Consumer<Error> errorConsumer);

}
