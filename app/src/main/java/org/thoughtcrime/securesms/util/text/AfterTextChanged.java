package org.thoughtcrime.securesms.util.text;

import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

public final class AfterTextChanged implements TextWatcher {

  private final Consumer<Editable> afterTextChangedConsumer;

  public AfterTextChanged(@NonNull Consumer<Editable> afterTextChangedConsumer) {
    this.afterTextChangedConsumer = afterTextChangedConsumer;
  }

  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) {
  }

  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
  }

  @Override
  public void afterTextChanged(Editable s) {
    afterTextChangedConsumer.accept(s);
  }
}
