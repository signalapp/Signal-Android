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
                                              pointer.incrementalMac != null ? Optional.of(pointer.incrementalMac.toByteArray()) : Optional.empty(),
                                              pointer.incrementalMacChunkSize != null ? pointer.incrementalMacChunkSize : 0,
                                              pointer.fileName != null ? Optional.of(pointer.fileName) : Optional.empty(),
                                              ((pointer.flags != null ? pointer.flags : 0) & FlagUtil.toBinaryFlag(AttachmentPointer.Flags.VOICE_MESSAGE.getValue())) != 0,
                                              ((pointer.flags != null ? pointer.flags : 0) & FlagUtil.toBinaryFlag(AttachmentPointer.Flags.BORDERLESS.getValue())) != 0,
                                              ((pointer.flags != null ? pointer.flags : 0) & FlagUtil.toBinaryFlag(AttachmentPointer.Flags.GIF.getValue())) != 0,
                                              pointer.caption != null ? Optional.of(pointer.caption) : Optional.empty(),
                                              pointer.blurHash != null ? Optional.of(pointer.blurHash) : Optional.empty(),
                                              pointer.uploadTimestamp != null ? pointer.uploadTimestamp : 0,
                                              UuidUtil.fromByteStringOrNull(pointer.uuid));
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
      builder.incrementalMac(ByteString.of(attachment.getIncrementalDigest().get()));
    }

    if (attachment.getIncrementalMacChunkSize() > 0) {
      builder.incrementalMacChunkSize(attachment.getIncrementalMacChunkSize());
    }

    if (attachment.getRemoteId() instanceof SignalServiceAttachmentRemoteId.V2) {
      builder.cdnId(((SignalServiceAttachmentRemoteId.V2) attachment.getRemoteId()).getCdnId());
    }

    if (attachment.getRemoteId() instanceof SignalServiceAttachmentRemoteId.V4) {
      builder.cdnKey(((SignalServiceAttachmentRemoteId.V4) attachment.getRemoteId()).getCdnKey());
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

    if (attachment.getUuid() != null) {
      builder.uuid(UuidUtil.toByteString(attachment.getUuid()));
    }

    return builder.build();
  }
}
