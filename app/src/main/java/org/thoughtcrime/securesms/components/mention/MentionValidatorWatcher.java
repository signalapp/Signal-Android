package org.thoughtcrime.securesms.components.mention;

import android.text.Annotation;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Provides a mechanism to validate mention annotations set on an edit text. This enables
 * removing invalid mentions if the user mentioned isn't in the group.
 */
public class MentionValidatorWatcher implements TextWatcher {

  @Nullable private List<Annotation> invalidMentionAnnotations;
  @Nullable private MentionValidator mentionValidator;

  @Override
  public void onTextChanged(CharSequence sequence, int start, int before, int count) {
    if (count > 1 && mentionValidator != null && sequence instanceof Spanned) {
      Spanned span = (Spanned) sequence;

      List<Annotation> mentionAnnotations = MentionAnnotation.getMentionAnnotations(span, start, start + count);

      if (mentionAnnotations.size() > 0) {
        invalidMentionAnnotations = mentionValidator.getInvalidMentionAnnotations(mentionAnnotations);
      }
    }
  }

  @Override
  public void afterTextChanged(Editable editable) {
    if (invalidMentionAnnotations == null) {
      return;
    }

    List<Annotation> invalidMentions = invalidMentionAnnotations;
    invalidMentionAnnotations = null;

    for (Annotation annotation : invalidMentions) {
      editable.removeSpan(annotation);
    }
  }

  public void setMentionValidator(@Nullable MentionValidator mentionValidator) {
    this.mentionValidator = mentionValidator;
  }

  @Override
  public void beforeTextChanged(CharSequence sequence, int start, int count, int after) { }

  public interface MentionValidator {
    List<Annotation> getInvalidMentionAnnotations(List<Annotation> mentionAnnotations);
  }
}
