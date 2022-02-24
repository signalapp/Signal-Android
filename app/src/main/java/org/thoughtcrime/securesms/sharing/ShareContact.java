package org.thoughtcrime.securesms.sharing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Objects;

public final class ShareContact {
  private final Optional<RecipientId> recipientId;
  private final String                number;

  public ShareContact(@NonNull RecipientId recipientId) {
    this.recipientId = Optional.of(recipientId);
    this.number      = null;
  }

  public ShareContact(@NonNull Optional<RecipientId> recipientId, @Nullable String number) {
    this.recipientId = recipientId;
    this.number      = number;
  }

  public Optional<RecipientId> getRecipientId() {
    return recipientId;
  }

  public String getNumber() {
    return number;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ShareContact that = (ShareContact) o;
    return recipientId.equals(that.recipientId) &&
           Objects.equals(number, that.number);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipientId, number);
  }
}
