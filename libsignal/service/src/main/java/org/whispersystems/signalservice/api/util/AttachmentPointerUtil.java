package org.whispersystems.signalservice.api.util;

import org.whispersystems.signalservice.api.InvalidMessageStructureException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.internal.push.AttachmentPointer;
import org.whispersystems.util.FlagUtil;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import okio.ByteString;

public final class AttachmentPointerUtil {
  public static SignalServiceAttachmentPointer createSignalAttachmentPointer(byte[] pointer) throws InvalidMessageStructureException, IOException {
    return createSignalAttachmentPointer(AttachmentPointer.ADAPTER.decode(pointer));
  }

  public static SignalServiceAttachmentPointer createSignalAttachmentPointer(AttachmentPointer pointer) throws InvalidMessageStructureException {
    return new SignalServiceAttachmentPointer(Objects.requireNonNull(pointer.cdnNumber),
                                              SignalServiceAttachmentRemoteId.from(pointer),
                                              pointer.contentType,
                                              Objects.requireNonNull(pointer.key).toByteArray(),
                                              pointer.size != null ? Optional.of(pointer.size) : Optional.empty(),
                                              pointer.thumbnail != null ? Optional.of(pointer.thumbnail.toByteArray()): Optional.empty(),
                                              pointer.width != null ? pointer.width : 0,
                                              pointer.height != null ? pointer.height : 0,
                                              pointer.digest != null ? Optional.of(pointer.digest.toByteArray()) : Optional.empty(),
                                              pointer.incrementalDigest != null ? Optional.of(pointer.incrementalDigest.toByteArray()) : Optional.empty(),
                                              pointer.fileName != null ? Optional.of(pointer.fileName) : Optional.empty(),
                                              ((pointer.flags != null ? pointer.flags : 0) & FlagUtil.toBinaryFlag(AttachmentPointer.Flags.VOICE_MESSAGE.getValue())) != 0,
                                              ((pointer.flags != null ? pointer.flags : 0) & FlagUtil.toBinaryFlag(AttachmentPointer.Flags.BORDERLESS.getValue())) != 0,
                                              ((pointer.flags != null ? pointer.flags : 0) & FlagUtil.toBinaryFlag(AttachmentPointer.Flags.GIF.getValue())) != 0,
                                              pointer.caption != null ? Optional.of(pointer.caption) : Optional.empty(),
                                              pointer.blurHash != null ? Optional.of(pointer.blurHash) : Optional.empty(),
                                              pointer.uploadTimestamp != null ? pointer.uploadTimestamp : 0);

  }

  public static AttachmentPointer createAttachmentPointer(SignalServiceAttachmentPointer attachment) {
    AttachmentPointer.Builder builder = new AttachmentPointer.Builder()
                                                             .cdnNumber(attachment.getCdnNumber())
                                                             .contentType(attachment.getContentType())
                                                             .key(ByteString.of(attachment.getKey()))
                                                             .digest(ByteString.of(attachment.getDigest().get()))
                                                             .size(attachment.getSize().get())
                                                             .uploadTimestamp(attachment.getUploadTimestamp());

    if (attachment.getIncrementalDigest().isPresent()) {
      builder.incrementalDigest(ByteString.of(attachment.getIncrementalDigest().get()));
    }

    if (attachment.getRemoteId().getV2().isPresent()) {
      builder.cdnId(attachment.getRemoteId().getV2().get());
    }

    if (attachment.getRemoteId().getV3().isPresent()) {
      builder.cdnKey(attachment.getRemoteId().getV3().get());
    }

    if (attachment.getFileName().isPresent()) {
      builder.fileName(attachment.getFileName().get());
    }

    if (attachment.getPreview().isPresent()) {
      builder.thumbnail(ByteString.of(attachment.getPreview().get()));
    }

    if (attachment.getWidth() > 0) {
      builder.width(attachment.getWidth());
    }

    if (attachment.getHeight() > 0) {
      builder.height(attachment.getHeight());
    }

    int flags = 0;

    if (attachment.getVoiceNote()) {
      flags |= FlagUtil.toBinaryFlag(AttachmentPointer.Flags.VOICE_MESSAGE.getValue());
    }

    if (attachment.isBorderless()) {
      flags |= FlagUtil.toBinaryFlag(AttachmentPointer.Flags.BORDERLESS.getValue());
    }

    if (attachment.isGif()) {
      flags |= FlagUtil.toBinaryFlag(AttachmentPointer.Flags.GIF.getValue());
    }

    builder.flags(flags);

    if (attachment.getCaption().isPresent()) {
      builder.caption(attachment.getCaption().get());
    }

    if (attachment.getBlurHash().isPresent()) {
      builder.blurHash(attachment.getBlurHash().get());
    }

    return builder.build();
  }
}
