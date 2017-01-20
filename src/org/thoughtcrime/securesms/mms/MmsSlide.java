package org.thoughtcrime.securesms.mms;


import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.attachments.Attachment;

public class MmsSlide extends ImageSlide {

  public MmsSlide(@NonNull Context context, @NonNull Attachment attachment) {
    super(context, attachment);
  }

  @NonNull
  @Override
  public String getContentDescription() {
    return "MMS";
  }

}
