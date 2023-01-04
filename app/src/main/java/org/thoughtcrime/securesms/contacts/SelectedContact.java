package org.thoughtcrime.securesms.contacts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

/**
 * Model for a contact and the various ways it could be represented. Used in situations where we
 * don't want to create Recipients for the wrapped data (like a custom-entered phone number for
 * someone you don't yet have a conversation with).
 */
public final class SelectedContact {
  private final RecipientId recipientId;
  private final String      number;
  private final String      username;

  public static @NonNull SelectedContact forPhone(@Nullable RecipientId recipientId, @NonNull String number) {
    return new SelectedContact(recipientId, number, null);
  }

  public static @NonNull SelectedContact forUsername(@Nullable RecipientId recipientId, @NonNull String username) {
    return new SelectedContact(recipientId, null, username);
  }

  public static @NonNull SelectedContact forRecipientId(@NonNull RecipientId recipientId) {
    return new SelectedContact(recipientId, null, null);
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

  public boolean hasUsername() {
    return username != null;
  }

  /**
   * Returns true when non-null recipient ids match, and false if not.
   * <p>
   * If one or more recipient id is not set, then it returns true iff any other non-null property
   * matches one on the other contact.
   */
  public boolean matches(@Nullable SelectedContact other) {
    if (other == null) return false;

    if (recipientId != null && other.recipientId != null) {
      return recipientId.equals(other.recipientId);
    }

    return number   != null && number  .equals(other.number)   ||
           username != null && username.equals(other.username);
  }
}
