package org.thoughtcrime.securesms.contacts.sync;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Objects;

/**
 * Represents all the data we pull from a Phone data cursor row from the contacts database.
 */
final class PhoneNumberRecord {

  private final RecipientId recipientId;
  private final String      displayName;
  private final String      contactPhotoUri;
  private final String      contactLabel;
  private final int         phoneType;
  private final Uri         contactUri;

  private PhoneNumberRecord(@NonNull PhoneNumberRecord.Builder builder) {
    recipientId     = Objects.requireNonNull(builder.recipientId);
    displayName     = builder.displayName;
    contactPhotoUri = builder.contactPhotoUri;
    contactLabel    = builder.contactLabel;
    phoneType       = builder.phoneType;
    contactUri      = builder.contactUri;
  }

  @NonNull RecipientId getRecipientId() {
    return recipientId;
  }

  @Nullable String getDisplayName() {
    return displayName;
  }

  @Nullable String getContactPhotoUri() {
    return contactPhotoUri;
  }

  @Nullable String getContactLabel() {
    return contactLabel;
  }

  int getPhoneType() {
    return phoneType;
  }

  @Nullable Uri getContactUri() {
    return contactUri;
  }

  final static class Builder {
    private RecipientId recipientId;
    private String      displayName;
    private String      contactPhotoUri;
    private String      contactLabel;
    private int         phoneType;
    private Uri         contactUri;

    @NonNull Builder withRecipientId(@NonNull RecipientId recipientId) {
      this.recipientId = recipientId;
      return this;
    }

    @NonNull Builder withDisplayName(@Nullable String displayName) {
      this.displayName = displayName;
      return this;
    }

    @NonNull Builder withContactUri(@Nullable Uri contactUri) {
      this.contactUri = contactUri;
      return this;
    }

    @NonNull Builder withContactLabel(@NonNull String contactLabel) {
      this.contactLabel = contactLabel;
      return this;
    }

    @NonNull Builder withContactPhotoUri(@NonNull String contactPhotoUri) {
      this.contactPhotoUri = contactPhotoUri;
      return this;
    }

    @NonNull Builder withPhoneType(int phoneType) {
      this.phoneType = phoneType;
      return this;
    }

    @NonNull PhoneNumberRecord build() {
      return new PhoneNumberRecord(this);
    }
  }
}
