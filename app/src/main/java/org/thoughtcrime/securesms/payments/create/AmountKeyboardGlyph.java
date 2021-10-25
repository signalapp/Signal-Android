package org.thoughtcrime.securesms.payments.create;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

enum AmountKeyboardGlyph {
  NONE(-1),
  ZERO(R.string.CreatePaymentFragment__0),
  ONE(R.string.CreatePaymentFragment__1),
  TWO(R.string.CreatePaymentFragment__2),
  THREE(R.string.CreatePaymentFragment__3),
  FOUR(R.string.CreatePaymentFragment__4),
  FIVE(R.string.CreatePaymentFragment__5),
  SIX(R.string.CreatePaymentFragment__6),
  SEVEN(R.string.CreatePaymentFragment__7),
  EIGHT(R.string.CreatePaymentFragment__8),
  NINE(R.string.CreatePaymentFragment__9),
  DECIMAL(R.string.CreatePaymentFragment__decimal),
  BACK(R.string.CreatePaymentFragment__lt);

  private final @StringRes int glyphRes;

  AmountKeyboardGlyph(int glyphRes) {
    this.glyphRes = glyphRes;
  }

  public String getGlyph(@NonNull Context context) {
    if (this == DECIMAL) {
      return ".";
    } else {
      return context.getString(glyphRes);
    }
  }
}
