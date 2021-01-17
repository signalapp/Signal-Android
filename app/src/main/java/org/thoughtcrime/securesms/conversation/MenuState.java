package org.thoughtcrime.securesms.conversation;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Set;

final class MenuState {

  private final boolean forward;
  private final boolean reply;
  private final boolean details;
  private final boolean saveAttachment;
  private final boolean resend;
  private final boolean copy;

  private MenuState(@NonNull Builder builder) {
    forward        = builder.forward;
    reply          = builder.reply;
    details        = builder.details;
    saveAttachment = builder.saveAttachment;
    resend         = builder.resend;
    copy           = builder.copy;
  }

  boolean shouldShowForwardAction() {
    return forward;
  }

  boolean shouldShowReplyAction() {
    return reply;
  }

  boolean shouldShowDetailsAction() {
    return details;
  }

  boolean shouldShowSaveAttachmentAction() {
    return saveAttachment;
  }

  boolean shouldShowResendAction() {
    return resend;
  }

  boolean shouldShowCopyAction() {
    return copy;
  }

  static MenuState getMenuState(@NonNull Recipient conversationRecipient,
                                @NonNull Set<MessageRecord> messageRecords,
                                boolean shouldShowMessageRequest)
  {
    
    Builder builder       = new Builder();
    boolean actionMessage = false;
    boolean hasText       = false;
    boolean sharedContact = false;
    boolean viewOnce      = false;
    boolean remoteDelete  = false;

    for (MessageRecord messageRecord : messageRecords) {
      if (isActionMessage(messageRecord))
      {
        actionMessage = true;
      }

      if (messageRecord.getBody().length() > 0) {
        hasText = true;
      }

      if (messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getSharedContacts().isEmpty()) {
        sharedContact = true;
      }

      if (messageRecord.isViewOnce()) {
        viewOnce = true;
      }

      if (messageRecord.isRemoteDelete()) {
        remoteDelete = true;
      }
    }

    if (messageRecords.size() > 1) {
      builder.shouldShowForwardAction(false)
             .shouldShowReplyAction(false)
             .shouldShowDetailsAction(false)
             .shouldShowSaveAttachmentAction(false)
             .shouldShowResendAction(false);
    } else {
      MessageRecord messageRecord = messageRecords.iterator().next();

      builder.shouldShowResendAction(messageRecord.isFailed())
             .shouldShowSaveAttachmentAction(!actionMessage                                              &&
                                             !viewOnce                                                   &&
                                             messageRecord.isMms()                                       &&
                                             !messageRecord.isMmsNotification()                          &&
                                             ((MediaMmsMessageRecord)messageRecord).containsMediaSlide() &&
                                             ((MediaMmsMessageRecord)messageRecord).getSlideDeck().getStickerSlide() == null)
             .shouldShowForwardAction(!actionMessage && !sharedContact && !viewOnce && !remoteDelete)
             .shouldShowDetailsAction(!actionMessage)
             .shouldShowReplyAction(canReplyToMessage(conversationRecipient, actionMessage, messageRecord, shouldShowMessageRequest));
    }

    return builder.shouldShowCopyAction(!actionMessage && !remoteDelete && hasText)
                  .build();
  }

  static boolean canReplyToMessage(@NonNull Recipient conversationRecipient, boolean actionMessage, @NonNull MessageRecord messageRecord, boolean isDisplayingMessageRequest) {
    return !actionMessage                                                              &&
           !messageRecord.isRemoteDelete()                                             &&
           !messageRecord.isPending()                                                  &&
           !messageRecord.isFailed()                                                   &&
           !isDisplayingMessageRequest                                                 &&
           messageRecord.isSecure()                                                    &&
           (!conversationRecipient.isGroup() || conversationRecipient.isActiveGroup()) &&
           !messageRecord.getRecipient().isBlocked();
  }

  static boolean isActionMessage(@NonNull MessageRecord messageRecord) {
    return messageRecord.isGroupAction()           ||
           messageRecord.isCallLog()               ||
           messageRecord.isJoined()                ||
           messageRecord.isExpirationTimerUpdate() ||
           messageRecord.isEndSession()            ||
           messageRecord.isIdentityUpdate()        ||
           messageRecord.isIdentityVerified()      ||
           messageRecord.isIdentityDefault()       ||
           messageRecord.isProfileChange()         ||
           messageRecord.isFailedDecryptionType();
  }

  private final static class Builder {

    private boolean forward;
    private boolean reply;
    private boolean details;
    private boolean saveAttachment;
    private boolean resend;
    private boolean copy;

    @NonNull Builder shouldShowForwardAction(boolean forward) {
      this.forward = forward;
      return this;
    }

    @NonNull Builder shouldShowReplyAction(boolean reply) {
      this.reply = reply;
      return this;
    }

    @NonNull Builder shouldShowDetailsAction(boolean details) {
      this.details = details;
      return this;
    }

    @NonNull Builder shouldShowSaveAttachmentAction(boolean saveAttachment) {
      this.saveAttachment = saveAttachment;
      return this;
    }

    @NonNull Builder shouldShowResendAction(boolean resend) {
      this.resend = resend;
      return this;
    }

    @NonNull Builder shouldShowCopyAction(boolean copy) {
      this.copy = copy;
      return this;
    }

    @NonNull
    MenuState build() {
      return new MenuState(this);
    }
  }
}
