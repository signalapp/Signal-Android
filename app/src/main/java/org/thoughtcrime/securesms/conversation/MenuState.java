package org.thoughtcrime.securesms.conversation;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.thoughtcrime.securesms.util.MessageConstraintsUtil;

import java.util.Set;
import java.util.stream.Collectors;

public final class MenuState {

  private static final int MAX_FORWARDABLE_COUNT = 32;

  private final boolean forward;
  private final boolean reply;
  private final boolean details;
  private final boolean saveAttachment;
  private final boolean resend;
  private final boolean copy;
  private final boolean delete;
  private final boolean reactions;
  private final boolean paymentDetails;
  private final boolean edit;

  private MenuState(@NonNull Builder builder) {
    forward        = builder.forward;
    reply          = builder.reply;
    details        = builder.details;
    saveAttachment = builder.saveAttachment;
    resend         = builder.resend;
    copy           = builder.copy;
    delete         = builder.delete;
    reactions      = builder.reactions;
    paymentDetails = builder.paymentDetails;
    edit           = builder.edit;
  }

  public boolean shouldShowForwardAction() {
    return forward;
  }

  public boolean shouldShowReplyAction() {
    return reply;
  }

  public boolean shouldShowDetailsAction() {
    return details;
  }

  public boolean shouldShowSaveAttachmentAction() {
    return saveAttachment;
  }

  public boolean shouldShowResendAction() {
    return resend;
  }

  public boolean shouldShowCopyAction() {
    return copy;
  }

  public boolean shouldShowDeleteAction() {
    return delete;
  }

  public boolean shouldShowReactions() {
    return reactions;
  }

  public boolean shouldShowPaymentDetails() {
    return paymentDetails;
  }

  public boolean shouldShowEditAction() {
    return edit;
  }

  public static MenuState getMenuState(@NonNull Recipient conversationRecipient,
                                       @NonNull Set<MultiselectPart> selectedParts,
                                       boolean shouldShowMessageRequest,
                                       boolean isNonAdminInAnnouncementGroup)
  {
    
    Builder builder         = new Builder();
    boolean actionMessage   = false;
    boolean hasText         = false;
    boolean sharedContact   = false;
    boolean viewOnce        = false;
    boolean remoteDelete    = false;
    boolean hasInMemory     = false;
    boolean hasPendingMedia = false;
    boolean mediaIsSelected = false;
    boolean hasGift         = false;
    boolean hasPayment       = false;

    for (MultiselectPart part : selectedParts) {
      MessageRecord messageRecord = part.getMessageRecord();

      if (isActionMessage(messageRecord)) {
        actionMessage = true;
        if (messageRecord.isInMemoryMessageRecord()) {
          hasInMemory = true;
        }
      }

      if (!(part instanceof MultiselectPart.Attachments)) {
        if (messageRecord.getBody().length() > 0) {
          hasText = true;
        }
      } else {
        mediaIsSelected = true;
        if (messageRecord.isMediaPending()) {
          hasPendingMedia = true;
        }
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

      if (MessageRecordUtil.hasGiftBadge(messageRecord)) {
        hasGift = true;
      }

      if (messageRecord.isPaymentNotification() || messageRecord.isPaymentTombstone()) {
        hasPayment = true;
      }
    }

    boolean shouldShowForwardAction = !actionMessage   &&
                                      !viewOnce        &&
                                      !remoteDelete    &&
                                      !hasPendingMedia &&
                                      !hasGift         &&
                                      !hasPayment      &&
                                      selectedParts.size() <= MAX_FORWARDABLE_COUNT;

    int uniqueRecords = selectedParts.stream()
                                     .map(MultiselectPart::getMessageRecord)
                                     .collect(Collectors.toSet())
                                     .size();

    if (uniqueRecords > 1) {
      builder.shouldShowForwardAction(shouldShowForwardAction)
             .shouldShowReplyAction(false)
             .shouldShowDetailsAction(false)
             .shouldShowSaveAttachmentAction(false)
             .shouldShowResendAction(false)
             .shouldShowEdit(false);
    } else {
      MultiselectPart multiSelectRecord = selectedParts.iterator().next();

      MessageRecord messageRecord = multiSelectRecord.getMessageRecord();

      builder.shouldShowResendAction(messageRecord.isFailed())
             .shouldShowSaveAttachmentAction(mediaIsSelected &&
                                             !actionMessage &&
                                             !viewOnce &&
                                             messageRecord.isMms() &&
                                             !hasPendingMedia &&
                                             !hasGift &&
                                             !messageRecord.isMmsNotification() &&
                                             ((MmsMessageRecord)messageRecord).containsMediaSlide() &&
                                             ((MmsMessageRecord)messageRecord).getSlideDeck().getStickerSlide() == null)
             .shouldShowForwardAction(shouldShowForwardAction)
             .shouldShowDetailsAction(!actionMessage && !conversationRecipient.isReleaseNotes())
             .shouldShowReplyAction(canReplyToMessage(conversationRecipient, actionMessage, messageRecord, shouldShowMessageRequest, isNonAdminInAnnouncementGroup));

      builder.shouldShowEdit(!actionMessage &&
                             hasText &&
                             !multiSelectRecord.getConversationMessage().getOriginalMessage().isFailed() &&
                             MessageConstraintsUtil.isValidEditMessageSend(multiSelectRecord.getConversationMessage().getOriginalMessage(), System.currentTimeMillis()));
    }

    return builder.shouldShowCopyAction(!actionMessage && !remoteDelete && hasText && !hasGift && !hasPayment)
                  .shouldShowDeleteAction(!hasInMemory && onlyContainsCompleteMessages(selectedParts))
                  .shouldShowReactions(!conversationRecipient.isReleaseNotes())
                  .shouldShowPaymentDetails(hasPayment)
                  .build();
  }

  private static boolean onlyContainsCompleteMessages(@NonNull Set<MultiselectPart> multiselectParts) {
    return multiselectParts.stream()
                           .map(MultiselectPart::getConversationMessage)
                           .map(ConversationMessage::getMultiselectCollection)
                           .allMatch(collection -> multiselectParts.containsAll(collection.toSet()));
  }

  public static boolean canReplyToMessage(@NonNull Recipient conversationRecipient,
                                          boolean actionMessage,
                                          @NonNull MessageRecord messageRecord,
                                          boolean isDisplayingMessageRequest,
                                          boolean isNonAdminInAnnouncementGroup)
  {
    return !actionMessage &&
           !isNonAdminInAnnouncementGroup &&
           !messageRecord.isRemoteDelete() &&
           !messageRecord.isPending() &&
           !messageRecord.isFailed() &&
           !isDisplayingMessageRequest &&
           messageRecord.isSecure() &&
           (!conversationRecipient.isGroup() || conversationRecipient.isActiveGroup()) &&
           !messageRecord.getFromRecipient().isBlocked() &&
           !conversationRecipient.isReleaseNotes();
  }

  public static boolean isActionMessage(@NonNull MessageRecord messageRecord) {
    return messageRecord.isInMemoryMessageRecord() || messageRecord.isUpdate();
  }

  private final static class Builder {

    private boolean forward;
    private boolean reply;
    private boolean details;
    private boolean saveAttachment;
    private boolean resend;
    private boolean copy;
    private boolean delete;
    private boolean reactions;
    private boolean paymentDetails;
    private boolean edit;

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

    @NonNull Builder shouldShowDeleteAction(boolean delete) {
      this.delete = delete;
      return this;
    }

    @NonNull Builder shouldShowReactions(boolean reactions) {
      this.reactions = reactions;
      return this;
    }

    @NonNull Builder shouldShowPaymentDetails(boolean paymentDetails) {
      this.paymentDetails = paymentDetails;
      return this;
    }

    @NonNull Builder shouldShowEdit(boolean edit) {
      this.edit = edit;
      return this;
    }

    @NonNull
    MenuState build() {
      return new MenuState(this);
    }
  }
}
