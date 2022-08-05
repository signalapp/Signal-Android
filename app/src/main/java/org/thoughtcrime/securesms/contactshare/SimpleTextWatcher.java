package org.thoughtcrime.securesms.contactshare;

import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;

public abstract class SimpleTextWatcher implements TextWatcher {

  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
    onTextChanged(s);
  }

  @Override
  public void afterTextChanged(Editable s) { }

  public void onTextChanged(@NonNull CharSequence text) {
    onTextChanged(text.toString());
  }

  public void onTextChanged(@NonNull String text) { }
}
