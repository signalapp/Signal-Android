package org.thoughtcrime.securesms.components.mention;

import android.text.Annotation;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;

import androidx.annotation.Nullable;

import static org.thoughtcrime.securesms.database.MentionUtil.MENTION_STARTER;

/**
 * Detects if some part of the mention is being deleted, and if so, deletes the entire mention and
 * span from the text view.
 */
public class MentionDeleter implements TextWatcher {

  @Nullable private Annotation toDelete;

  @Override
  public void beforeTextChanged(CharSequence sequence, int start, int count, int after) {
    if (count > 0 && sequence instanceof Spanned) {
      Spanned text = (Spanned) sequence;

      for (Annotation annotation : MentionAnnotation.getMentionAnnotations(text, start, start + count)) {
        if (text.getSpanStart(annotation) < start && text.getSpanEnd(annotation) > start) {
          toDelete = annotation;
          return;
        }
      }
    }
  }

  @Override
  public void afterTextChanged(Editable editable) {
    if (toDelete == null) {
      return;
    }

    int toDeleteStart = editable.getSpanStart(toDelete);
    int toDeleteEnd   = editable.getSpanEnd(toDelete);
    editable.removeSpan(toDelete);
    toDelete = null;

    editable.replace(toDeleteStart, toDeleteEnd, String.valueOf(MENTION_STARTER));
  }

  @Override
  public void onTextChanged(CharSequence sequence, int start, int before, int count) { }
}
