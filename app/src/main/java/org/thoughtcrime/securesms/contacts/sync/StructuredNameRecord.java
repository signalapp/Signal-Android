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

  public StructuredNameRecord(@Nullable String givenName, @Nullable String familyName) {
    this.givenName  = givenName;
    this.familyName = familyName;
  }

  public boolean hasGivenName() {
    return givenName != null;
  }

  public @NonNull ProfileName asProfileName() {
    return ProfileName.fromParts(givenName, familyName);
  }
}
