package org.thoughtcrime.securesms.conversation.ui.error;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * Wrapper class for helping show a list of recipients that had recent safety number changes.
 *
 * Also provides helper methods for behavior used in multiple spots.
 */
final class ChangedRecipient {
  private final Recipient      recipient;
  private final IdentityRecord record;

  ChangedRecipient(@NonNull Recipient recipient, @NonNull IdentityRecord record) {
    this.recipient = recipient;
    this.record    = record;
  }

  @NonNull Recipient getRecipient() {
    return recipient;
  }

  @NonNull IdentityRecord getIdentityRecord() {
    return record;
  }

  boolean isUnverified() {
    return record.getVerifiedStatus() == IdentityTable.VerifiedStatus.UNVERIFIED;
  }

  boolean isVerified() {
    return record.getVerifiedStatus() == IdentityTable.VerifiedStatus.VERIFIED;
  }

  @Override
  public String toString() {
    return "ChangedRecipient{" + "recipient=" + recipient.getId() + ", record=" + record.getIdentityKey().hashCode() + '}';
  }
}
