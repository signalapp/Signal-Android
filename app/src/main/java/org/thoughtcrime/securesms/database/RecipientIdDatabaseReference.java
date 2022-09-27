package org.thoughtcrime.securesms.database;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.RecipientId;

/**
 * Indicates that this table references a RecipientId. RecipientIds can be remapped at runtime if recipients merge, and therefore this table needs to be able to
 * handle remapping one RecipientId to another.
 */
interface RecipientIdDatabaseReference {
  void remapRecipient(@NonNull RecipientId fromId, @NonNull RecipientId toId);
}
