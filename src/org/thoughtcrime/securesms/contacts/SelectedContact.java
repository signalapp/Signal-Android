package org.thoughtcrime.securesms.contacts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Objects;

/**
 * Model for a contact and the various ways it could be represented. Used in situations where we
 * don't want to create Recipients for the wrapped data (like a custom-entered phone number for
 * someone you don't yet have a conversation with).
 *
 * Designed so that two instances will be equal if *any* of its properties match.
 */
public class SelectedContact {
  private final RecipientId recipientId;
  private final String      number;
  private final String      username;

  public static @NonNull SelectedContact forPhone(@Nullable RecipientId recipientId, @NonNull String number) {
    return new SelectedContact(recipientId, number, null);
  }

  public static @NonNull SelectedContact forUsername(@Nullable RecipientId recipientId, @NonNull String username) {
    return new SelectedContact(recipientId, null, username);
  }

  private SelectedContact(@Nullable RecipientId recipientId, @Nullable String number, @Nullable String username) {
    this.recipientId = recipientId;
    this.number      = number;
    this.username    = username;
  }

  public @NonNull RecipientId getOrCreateRecipientId(@NonNull Context context) {
    if (recipientId != null) {
      return recipientId;
    } else if (number != null) {
      return Recipient.external(context, number).getId();
    } else {
      throw new AssertionError();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SelectedContact that = (SelectedContact) o;

    return Objects.equals(recipientId, that.recipientId) ||
           Objects.equals(number, that.number)           ||
           Objects.equals(username, that.username);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipientId, number, username);
  }
}
