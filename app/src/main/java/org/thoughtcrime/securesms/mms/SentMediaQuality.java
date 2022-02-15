package org.thoughtcrime.securesms.mms;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

import java.util.Arrays;

/**
 * Quality levels to send media at.
 */
public enum SentMediaQuality {
  STANDARD(0, R.string.DataAndStorageSettingsFragment__standard),
  HIGH(1, R.string.DataAndStorageSettingsFragment__high);


  private final int code;
  private final int label;

  SentMediaQuality(int code, @StringRes int label) {
    this.code  = code;
    this.label = label;
  }

  public static @NonNull SentMediaQuality fromCode(int code) {
    if (HIGH.code == code) {
      return HIGH;
    }
    return STANDARD;
  }

  public static @NonNull String[] getLabels(@NonNull Context context) {
    return Arrays.stream(values()).map(q -> context.getString(q.label)).toArray(String[]::new);
  }

  public int getCode() {
    return code;
  }
}
