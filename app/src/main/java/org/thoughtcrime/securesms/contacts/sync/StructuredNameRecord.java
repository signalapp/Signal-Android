package org.thoughtcrime.securesms.contacts.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.profiles.ProfileName;

/**
 * Represents the data pulled from a StructuredName row of a Contacts data cursor.
 */
final class StructuredNameRecord {
  private final String givenName;
  private final String familyName;

  StructuredNameRecord(@NonNull StructuredNameRecord.Builder builder) {
    this.givenName  = builder.givenName;
    this.familyName = builder.familyName;
  }

  public boolean hasGivenName() {
    return givenName != null;
  }

  public @NonNull ProfileName asProfileName() {
    return ProfileName.fromParts(givenName, familyName);
  }

  final static class Builder {
    private String givenName;
    private String familyName;

    @NonNull Builder withGivenName(@Nullable String givenName) {
      this.givenName = givenName;
      return this;
    }

    @NonNull Builder withFamilyName(@Nullable String familyName) {
      this.familyName = familyName;
      return this;
    }

    @NonNull StructuredNameRecord build() {
      return new StructuredNameRecord(this);
    }
  }
}
