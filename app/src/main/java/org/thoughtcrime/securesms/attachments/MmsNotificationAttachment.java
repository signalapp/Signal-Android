package org.thoughtcrime.securesms.attachments;


import android.net.Uri;

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.MessageTable;

public class MmsNotificationAttachment extends Attachment {

  public MmsNotificationAttachment(int status, long size) {
    super("application/mms", getTransferStateFromStatus(status), size, null, 0, null, null, null, null, null, false, false, false, 0, 0, false, 0, null, null, null, null, null);
  }

  @Nullable
  @Override
  public Uri getUri() {
    return null;
  }

  @Override
  public @Nullable Uri getPublicUri() {
    return null;
  }

  private static int getTransferStateFromStatus(int status) {
    if (status == MessageTable.MmsStatus.DOWNLOAD_INITIALIZED ||
        status == MessageTable.MmsStatus.DOWNLOAD_NO_CONNECTIVITY)
    {
      return AttachmentTable.TRANSFER_PROGRESS_PENDING;
    } else if (status == MessageTable.MmsStatus.DOWNLOAD_CONNECTING) {
      return AttachmentTable.TRANSFER_PROGRESS_STARTED;
    } else {
      return AttachmentTable.TRANSFER_PROGRESS_FAILED;
    }
  }
}
