package org.thoughtcrime.securesms.attachments;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.AttachmentDatabase;

/**
 * An attachment that represents where an attachment used to be. Useful when you need to know that
 * a message had an attachment and some metadata about it (like the contentType), even though the
 * underlying media no longer exists. An example usecase would be view-once messages, so that we can
 * quote them and know their contentType even though the media has been deleted.
 */
public class TombstoneAttachment extends Attachment {

  public TombstoneAttachment(@NonNull String contentType, boolean quote) {
    super(contentType, AttachmentDatabase.TRANSFER_PROGRESS_DONE, 0, null, null, null, null, null, null, false, 0, 0, quote, null, null, null, null);
  }

  @Override
  public @Nullable Uri getDataUri() {
    return null;
  }

  @Override
  public @Nullable Uri getThumbnailUri() {
    return null;
  }
}
