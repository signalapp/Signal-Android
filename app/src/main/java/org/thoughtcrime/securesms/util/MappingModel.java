package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

public interface MappingModel<T> {
  boolean areItemsTheSame(@NonNull T newItem);
  boolean areContentsTheSame(@NonNull T newItem);
}
