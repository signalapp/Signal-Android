package org.thoughtcrime.securesms.mediasend;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * Allow multiple transforms to operate on {@link Media}. Care should
 * be taken on the order and implementation of combined transformers to prevent
 * one undoing the work of the other.
 */
public final class CompositeMediaTransform implements MediaTransform {

  private final MediaTransform[] transforms;

  public CompositeMediaTransform(MediaTransform ...transforms) {
    this.transforms = transforms;
  }

  @Override
  public @NonNull Media transform(@NonNull Context context, @NonNull Media media) {
    Media updatedMedia = media;
    for (MediaTransform transform : transforms) {
      updatedMedia = transform.transform(context, updatedMedia);
    }
    return updatedMedia;
  }
}
