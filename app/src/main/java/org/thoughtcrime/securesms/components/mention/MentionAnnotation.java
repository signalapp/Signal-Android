package org.thoughtcrime.securesms.components.mention;


import android.text.Annotation;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * Factory for creating mention annotation spans.
 *
 * Note: This wraps creating an Android standard {@link Annotation} so it can leverage the built in
 * span parceling for copy/paste. Do not extend Annotation or this will be lost.
 */
public final class MentionAnnotation {

  public static final String MENTION_ANNOTATION = "mention";

  private MentionAnnotation() {
  }

  public static Annotation mentionAnnotationForUuid(@NonNull UUID uuid) {
    return new Annotation(MENTION_ANNOTATION, uuid.toString());
  }
}
