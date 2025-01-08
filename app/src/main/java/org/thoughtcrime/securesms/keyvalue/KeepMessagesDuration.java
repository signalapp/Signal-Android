package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

import java.util.concurrent.TimeUnit;

public enum KeepMessagesDuration {
  FOREVER(0, R.string.preferences_storage__forever, Long.MAX_VALUE),
  ONE_YEAR(1, R.string.preferences_storage__one_year, TimeUnit.DAYS.toMillis(366)),
  SIX_MONTHS(2, R.string.preferences_storage__six_months, TimeUnit.DAYS.toMillis(183)),
  THIRTY_DAYS(3, R.string.preferences_storage__thirty_days, TimeUnit.DAYS.toMillis(30));

  private final int  id;
  private final int  stringResource;
  private final long duration;

  KeepMessagesDuration(int id, @StringRes int stringResource, long duration) {
    this.id             = id;
    this.stringResource = stringResource;
    this.duration       = duration;
  }

  public int getId() {
    return id;
  }

  public @StringRes int getStringResource() {
    return stringResource;
  }

  public long getDuration() {
    return duration;
  }

  public static @NonNull KeepMessagesDuration fromId(int id) {
    return values()[id];
  }
}
