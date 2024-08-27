/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.restorelocalbackup;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.BackupUtil;

public class PassphraseAsYouTypeFormatter implements TextWatcher {

  private static final int GROUP_SIZE = 5;

  @Override
  public void afterTextChanged(Editable editable) {
    removeSpans(editable);

    addSpans(editable);
  }

  private static void removeSpans(Editable editable) {
    SpaceSpan[] paddingSpans = editable.getSpans(0, editable.length(), SpaceSpan.class);

    for (SpaceSpan span : paddingSpans) {
      editable.removeSpan(span);
    }
  }

  private static void addSpans(Editable editable) {
    final int length = editable.length();

    for (int i = GROUP_SIZE; i < length; i += GROUP_SIZE) {
      editable.setSpan(new SpaceSpan(), i - 1, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    if (editable.length() > BackupUtil.PASSPHRASE_LENGTH) {
      editable.delete(BackupUtil.PASSPHRASE_LENGTH, editable.length());
    }
  }

  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) {
  }

  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
  }

  /**
   * A {@link ReplacementSpan} adds a small space after a single character.
   * Based on https://stackoverflow.com/a/51949578
   */
  private static class SpaceSpan extends ReplacementSpan {

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
      return (int) (paint.measureText(text, start, end) * 1.7f);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
      canvas.drawText(text.subSequence(start, end).toString(), x, y, paint);
    }
  }
}
