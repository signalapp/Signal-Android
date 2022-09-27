package org.thoughtcrime.securesms.database;

interface RecipientIdDatabaseReference {
  void remapRecipient(RecipientId fromId, RecipientId toId);
}
