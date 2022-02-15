package org.thoughtcrime.securesms.payments.preferences.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

public class NoRecentActivity implements MappingModel<NoRecentActivity> {
  @Override
  public boolean areItemsTheSame(@NonNull NoRecentActivity newItem) {
    return true;
  }

  @Override
  public boolean areContentsTheSame(@NonNull NoRecentActivity newItem) {
    return true;
  }
}
