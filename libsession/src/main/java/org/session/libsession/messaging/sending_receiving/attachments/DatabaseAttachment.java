package org.session.libsession.messaging.sending_receiving.attachments;

import android.net.Uri;

import androidx.annotation.Nullable;

import org.session.libsession.messaging.MessagingModuleConfiguration;

public class DatabaseAttachment extends Attachment {

  private final AttachmentId attachmentId;
  private final long         mmsId;
  private final boolean      hasData;
  private final boolean      hasThumbnail;
  private boolean isUploaded = false;

  public DatabaseAttachment(AttachmentId attachmentId, long mmsId,
                            boolean hasData, boolean hasThumbnail,
                            String contentType, int transferProgress, long size,
                            String fileName, String location, String key, String relay,
                            byte[] digest, String fastPreflightId, boolean voiceNote,
                            int width, int height, boolean quote, @Nullable String caption,
                            String url)
  {
    super(contentType, transferProgress, size, fileName, location, key, relay, digest, fastPreflightId, voiceNote, width, height, quote, caption, url);
    this.attachmentId = attachmentId;
    this.hasData      = hasData;
    this.hasThumbnail = hasThumbnail;
    this.mmsId        = mmsId;
  }

  @Override
  @Nullable
  public Uri getDataUri() {
    if (hasData) {
      return MessagingModuleConfiguration.shared.getStorage().getAttachmentDataUri(attachmentId);
    } else {
      return null;
    }
  }

  @Override
  @Nullable
  public Uri getThumbnailUri() {
    if (hasThumbnail) {
      return MessagingModuleConfiguration.shared.getStorage().getAttachmentThumbnailUri(attachmentId);
    } else {
      return null;
    }
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

  public boolean hasThumbnail() {
    return hasThumbnail;
  }

  public boolean isUploaded() {
    return isUploaded;
  }

  public void setUploaded(boolean uploaded) {
    isUploaded = uploaded;
  }
}
