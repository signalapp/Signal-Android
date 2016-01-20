package org.privatechats.securesms.attachments;

import android.net.Uri;
import android.support.annotation.NonNull;

import org.privatechats.securesms.mms.PartAuthority;

public class DatabaseAttachment extends Attachment {

  private final AttachmentId attachmentId;
  private final long         mmsId;
  private final boolean      hasData;

  public DatabaseAttachment(AttachmentId attachmentId, long mmsId,  boolean hasData,
                            String contentType, int transferProgress, long size,
                            String location, String key, String relay)
  {
    super(contentType, transferProgress, size, location, key, relay);
    this.attachmentId = attachmentId;
    this.hasData      = hasData;
    this.mmsId        = mmsId;
  }

  @Override
  @NonNull
  public Uri getDataUri() {
    return PartAuthority.getAttachmentDataUri(attachmentId);
  }

  @Override
  @NonNull
  public Uri getThumbnailUri() {
    return PartAuthority.getAttachmentThumbnailUri(attachmentId);
  }

  public AttachmentId getAttachmentId() {
    return attachmentId;
  }

  @Override
  public boolean equals(Object other) {
    return other != null &&
           other instanceof DatabaseAttachment &&
           ((DatabaseAttachment) other).attachmentId.equals(this.attachmentId);
  }

  @Override
  public int hashCode() {
    return attachmentId.hashCode();
  }

  public long getMmsId() {
    return mmsId;
  }

  public boolean hasData() {
    return hasData;
  }
}
