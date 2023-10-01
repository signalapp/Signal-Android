package org.thoughtcrime.securesms.mms;


import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.util.MediaUtil;

/**
 * Slide used for attachments with contentType {@link MediaUtil#VIEW_ONCE}.
 * Attachments will only get this type *after* they've been viewed, or if they were synced from a
 * linked device. Incoming unviewed messages will have the appropriate image/video contentType.
 */
public class ViewOnceSlide extends Slide {

  public ViewOnceSlide(@NonNull Attachment attachment) {
    super(attachment);
  }

  @Override
  public boolean hasViewOnce() {
    return true;
  }
}
