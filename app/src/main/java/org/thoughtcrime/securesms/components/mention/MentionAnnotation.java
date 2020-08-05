package org.thoughtcrime.securesms.components.mention;


import android.text.Annotation;
import android.text.Spannable;
import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Collections;
import java.util.List;

/**
 * This wraps an Android standard {@link Annotation} so it can leverage the built in
 * span parceling for copy/paste. The annotation span contains the mentioned recipient's
 * id (in numerical form).
 *
 * Note: Do not extend Annotation or the parceling behavior will be lost.
 */
public final class MentionAnnotation {

  public static final String MENTION_ANNOTATION = "mention";

  private MentionAnnotation() {
  }

  public static Annotation mentionAnnotationForRecipientId(@NonNull RecipientId id) {
    return new Annotation(MENTION_ANNOTATION, idToMentionAnnotationValue(id));
  }

  public static String idToMentionAnnotationValue(@NonNull RecipientId id) {
    return String.valueOf(id.toLong());
  }

  public static boolean isMentionAnnotation(@NonNull Annotation annotation) {
    return MENTION_ANNOTATION.equals(annotation.getKey());
  }

  public static void setMentionAnnotations(Spannable body, List<Mention> mentions) {
    for (Mention mention : mentions) {
      body.setSpan(MentionAnnotation.mentionAnnotationForRecipientId(mention.getRecipientId()), mention.getStart(), mention.getStart() + mention.getLength(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  public static @NonNull List<Mention> getMentionsFromAnnotations(@Nullable CharSequence text) {
    if (text instanceof Spanned) {
      Spanned spanned = (Spanned) text;
      return Stream.of(getMentionAnnotations(spanned))
                   .map(annotation -> {
                     int spanStart  = spanned.getSpanStart(annotation);
                     int spanLength = spanned.getSpanEnd(annotation) - spanStart;
                     return new Mention(RecipientId.from(annotation.getValue()), spanStart, spanLength);
                   })
                   .toList();
    }
    return Collections.emptyList();
  }

  public static @NonNull List<Annotation> getMentionAnnotations(@NonNull Spanned spanned) {
    return getMentionAnnotations(spanned, 0, spanned.length());
  }

  public static @NonNull List<Annotation> getMentionAnnotations(@NonNull Spanned spanned, int start, int end) {
    return Stream.of(spanned.getSpans(start, end, Annotation.class))
                 .filter(MentionAnnotation::isMentionAnnotation)
                 .toList();
  }
}
