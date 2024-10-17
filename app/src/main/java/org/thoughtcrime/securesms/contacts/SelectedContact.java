package org.thoughtcrime.securesms.contacts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.contacts.paged.ChatType;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey;
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
  private final ChatType    chatType;

  public static @NonNull SelectedContact forPhone(@Nullable RecipientId recipientId, @NonNull String number) {
    return new SelectedContact(recipientId, number, null, null);
  }

  public static @NonNull SelectedContact forUsername(@Nullable RecipientId recipientId, @NonNull String username) {
    return new SelectedContact(recipientId, null, username, null);
  }

  public static @NonNull SelectedContact forChatType(@NonNull ChatType chatType) {
    return new SelectedContact(null, null, null, chatType);
  }

  public static @NonNull SelectedContact forRecipientId(@NonNull RecipientId recipientId) {
    return new SelectedContact(recipientId, null, null, null);
  }

  private SelectedContact(@Nullable RecipientId recipientId, @Nullable String number, @Nullable String username, @Nullable ChatType chatType) {
    this.recipientId = recipientId;
    this.number      = number;
    this.username    = username;
    this.chatType    = chatType;
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

  public @Nullable RecipientId getRecipientId() {
    return recipientId;
  }

  public @Nullable String getNumber() {
    return number;
  }

  public boolean hasUsername() {
    return username != null;
  }

  public boolean hasChatType() {
    return chatType != null;
  }

  public ChatType getChatType() {
    return chatType;
  }

  public @NonNull ContactSearchKey toContactSearchKey() {
    if (recipientId != null) {
      return new ContactSearchKey.RecipientSearchKey(recipientId, false);
    } else if (number != null) {
      return new ContactSearchKey.UnknownRecipientKey(ContactSearchConfiguration.SectionKey.PHONE_NUMBER, number);
    } else if (username != null) {
      return new ContactSearchKey.UnknownRecipientKey(ContactSearchConfiguration.SectionKey.USERNAME, username);
    } else if (chatType != null) {
      return new ContactSearchKey.ChatTypeSearchKey(chatType);
    } else {
      throw new IllegalStateException("Nothing to map!");
    }
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
           username != null && username.equals(other.username) ||
           chatType != null && chatType.equals(other.chatType);
  }
}
