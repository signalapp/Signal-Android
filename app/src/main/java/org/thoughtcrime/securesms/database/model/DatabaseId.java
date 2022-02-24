package org.thoughtcrime.securesms.database.model;

import androidx.annotation.NonNull;

public interface DatabaseId {
  @NonNull String serialize();
}
