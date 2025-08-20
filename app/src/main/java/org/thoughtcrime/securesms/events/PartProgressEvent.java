package org.thoughtcrime.securesms.events;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress;

public final class PartProgressEvent {

  public final Attachment attachment;
  public final Type       type;
  public final long       total;
  public final long       progress;

  public enum Type {
    COMPRESSION,
    NETWORK
  }

  public PartProgressEvent(@NonNull Attachment attachment, @NonNull Type type, long total, long progress) {
    this.attachment = attachment;
    this.type       = type;
    this.total      = total;
    this.progress   = progress;
  }

  public PartProgressEvent(@NonNull Attachment attachment, @NonNull Type type, @NonNull AttachmentTransferProgress progress) {
    this.attachment = attachment;
    this.type       = type;
    this.total      = progress.getTotal().getInWholeBytes();
    this.progress   = progress.getTransmitted().getInWholeBytes();
  }
}
