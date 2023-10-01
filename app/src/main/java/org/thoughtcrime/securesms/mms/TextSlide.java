package org.thoughtcrime.securesms.mms;


import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.util.MediaUtil;

public class TextSlide extends Slide {

  public TextSlide(@NonNull Attachment attachment) {
    super(attachment);
  }

  public TextSlide(@NonNull Context context, @NonNull Uri uri, @Nullable String filename, long size) {
    super(constructAttachmentFromUri(context, uri, MediaUtil.LONG_TEXT, size, 0, 0, true, filename, null, null, null, null, false, false, false, false));
  }
}
