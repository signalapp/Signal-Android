package org.whispersystems.signalservice.api.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.InvalidMessageStructureException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.util.FlagUtil;

public final class AttachmentPointerUtil {
  public static SignalServiceAttachmentPointer createSignalAttachmentPointer(byte[] pointer) throws InvalidMessageStructureException, InvalidProtocolBufferException {
    return createSignalAttachmentPointer(SignalServiceProtos.AttachmentPointer.parseFrom(pointer));
  }

  public static SignalServiceAttachmentPointer createSignalAttachmentPointer(SignalServiceProtos.AttachmentPointer pointer) throws InvalidMessageStructureException {
    return new SignalServiceAttachmentPointer(pointer.getCdnNumber(),
                                              SignalServiceAttachmentRemoteId.from(pointer),
                                              pointer.getContentType(),
                                              pointer.getKey().toByteArray(),
                                              pointer.hasSize() ? Optional.of(pointer.getSize()) : Optional.<Integer>absent(),
                                              pointer.hasThumbnail() ? Optional.of(pointer.getThumbnail().toByteArray()): Optional.<byte[]>absent(),
                                              pointer.getWidth(), pointer.getHeight(),
                                              pointer.hasDigest() ? Optional.of(pointer.getDigest().toByteArray()) : Optional.<byte[]>absent(),
                                              pointer.hasFileName() ? Optional.of(pointer.getFileName()) : Optional.<String>absent(),
                                              (pointer.getFlags() & FlagUtil.toBinaryFlag(SignalServiceProtos.AttachmentPointer.Flags.VOICE_MESSAGE_VALUE)) != 0,
                                              (pointer.getFlags() & FlagUtil.toBinaryFlag(SignalServiceProtos.AttachmentPointer.Flags.BORDERLESS_VALUE)) != 0,
                                              (pointer.getFlags() & FlagUtil.toBinaryFlag(SignalServiceProtos.AttachmentPointer.Flags.GIF_VALUE)) != 0,
                                              pointer.hasCaption() ? Optional.of(pointer.getCaption()) : Optional.<String>absent(),
                                              pointer.hasBlurHash() ? Optional.of(pointer.getBlurHash()) : Optional.<String>absent(),
                                              pointer.hasUploadTimestamp() ? pointer.getUploadTimestamp() : 0);

  }

  public static SignalServiceProtos.AttachmentPointer createAttachmentPointer(SignalServiceAttachmentPointer attachment) {
    SignalServiceProtos.AttachmentPointer.Builder builder = SignalServiceProtos.AttachmentPointer.newBuilder()
                                                                                                 .setCdnNumber(attachment.getCdnNumber())
                                                                                                 .setContentType(attachment.getContentType())
                                                                                                 .setKey(ByteString.copyFrom(attachment.getKey()))
                                                                                                 .setDigest(ByteString.copyFrom(attachment.getDigest().get()))
                                                                                                 .setSize(attachment.getSize().get())
                                                                                                 .setUploadTimestamp(attachment.getUploadTimestamp());

    if (attachment.getRemoteId().getV2().isPresent()) {
      builder.setCdnId(attachment.getRemoteId().getV2().get());
    }

    if (attachment.getRemoteId().getV3().isPresent()) {
      builder.setCdnKey(attachment.getRemoteId().getV3().get());
    }

    if (attachment.getFileName().isPresent()) {
      builder.setFileName(attachment.getFileName().get());
    }

    if (attachment.getPreview().isPresent()) {
      builder.setThumbnail(ByteString.copyFrom(attachment.getPreview().get()));
    }

    if (attachment.getWidth() > 0) {
      builder.setWidth(attachment.getWidth());
    }

    if (attachment.getHeight() > 0) {
      builder.setHeight(attachment.getHeight());
    }

    int flags = 0;

    if (attachment.getVoiceNote()) {
      flags |= FlagUtil.toBinaryFlag(SignalServiceProtos.AttachmentPointer.Flags.VOICE_MESSAGE_VALUE);
    }

    if (attachment.isBorderless()) {
      flags |= FlagUtil.toBinaryFlag(SignalServiceProtos.AttachmentPointer.Flags.BORDERLESS_VALUE);
    }

    if (attachment.isGif()) {
      flags |= FlagUtil.toBinaryFlag(SignalServiceProtos.AttachmentPointer.Flags.GIF_VALUE);
    }

    builder.setFlags(flags);

    if (attachment.getCaption().isPresent()) {
      builder.setCaption(attachment.getCaption().get());
    }

    if (attachment.getBlurHash().isPresent()) {
      builder.setBlurHash(attachment.getBlurHash().get());
    }

    return builder.build();
  }
}
