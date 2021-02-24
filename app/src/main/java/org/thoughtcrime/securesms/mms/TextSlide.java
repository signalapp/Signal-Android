package org.thoughtcrime.securesms.mms;


import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.utilities.MediaTypes;

public class TextSlide extends Slide {

  public TextSlide(@NonNull Context context, @NonNull Attachment attachment) {
    super(context, attachment);
  }

  public TextSlide(@NonNull Context context, @NonNull Uri uri, @Nullable String filename, long size) {
    super(context, constructAttachmentFromUri(context, uri, MediaTypes.LONG_TEXT, size, 0, 0, true, filename, null, false, false));
  }
}
