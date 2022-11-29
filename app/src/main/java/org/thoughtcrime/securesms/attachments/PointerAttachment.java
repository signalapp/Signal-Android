package org.thoughtcrime.securesms.attachments;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class PointerAttachment extends Attachment {

  private PointerAttachment(@NonNull String contentType,
                            int transferState,
                            long size,
                            @Nullable String fileName,
                            int cdnNumber,
                            @NonNull String location,
                            @Nullable String key,
                            @Nullable String relay,
                            @Nullable byte[] digest,
                            @Nullable String fastPreflightId,
                            boolean voiceNote,
                            boolean borderless,
                            boolean videoGif,
                            int width,
                            int height,
                            long uploadTimestamp,
                            @Nullable String caption,
                            @Nullable StickerLocator stickerLocator,
                            @Nullable BlurHash blurHash)
  {
    super(contentType, transferState, size, fileName, cdnNumber, location, key, relay, digest, fastPreflightId, voiceNote, borderless, videoGif, width, height, false, uploadTimestamp, caption, stickerLocator, blurHash, null, null);
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

  public static List<Attachment> forPointers(Optional<List<SignalServiceAttachment>> pointers) {
    List<Attachment> results = new LinkedList<>();

    if (pointers.isPresent()) {
      for (SignalServiceAttachment pointer : pointers.get()) {
        Optional<Attachment> result = forPointer(Optional.of(pointer));

        if (result.isPresent()) {
          results.add(result.get());
        }
      }
    }

    return results;
  }

  public static List<Attachment> forPointers(List<SignalServiceDataMessage.Quote.QuotedAttachment> pointers) {
    List<Attachment> results = new LinkedList<>();

    if (pointers != null) {
      for (SignalServiceDataMessage.Quote.QuotedAttachment pointer : pointers) {
        Optional<Attachment> result = forPointer(pointer);

        if (result.isPresent()) {
          results.add(result.get());
        }
      }
    }

    return results;
  }

  public static Optional<Attachment> forPointer(Optional<SignalServiceAttachment> pointer) {
    return forPointer(pointer, null, null);
  }

  public static Optional<Attachment> forPointer(Optional<SignalServiceAttachment> pointer, @Nullable StickerLocator stickerLocator) {
    return forPointer(pointer, stickerLocator, null);
  }

  public static Optional<Attachment> forPointer(Optional<SignalServiceAttachment> pointer, @Nullable StickerLocator stickerLocator, @Nullable String fastPreflightId) {
    if (!pointer.isPresent() || !pointer.get().isPointer()) return Optional.empty();

    String encodedKey = null;

    if (pointer.get().asPointer().getKey() != null) {
      encodedKey = Base64.encodeBytes(pointer.get().asPointer().getKey());
    }

    return Optional.of(new PointerAttachment(pointer.get().getContentType(),
                                             AttachmentTable.TRANSFER_PROGRESS_PENDING,
                                             pointer.get().asPointer().getSize().orElse(0),
                                             pointer.get().asPointer().getFileName().orElse(null),
                                             pointer.get().asPointer().getCdnNumber(),
                                             pointer.get().asPointer().getRemoteId().toString(),
                                             encodedKey, null,
                                             pointer.get().asPointer().getDigest().orElse(null),
                                             fastPreflightId,
                                             pointer.get().asPointer().getVoiceNote(),
                                             pointer.get().asPointer().isBorderless(),
                                             pointer.get().asPointer().isGif(),
                                             pointer.get().asPointer().getWidth(),
                                             pointer.get().asPointer().getHeight(),
                                             pointer.get().asPointer().getUploadTimestamp(),
                                             pointer.get().asPointer().getCaption().orElse(null),
                                             stickerLocator,
                                             BlurHash.parseOrNull(pointer.get().asPointer().getBlurHash().orElse(null))));

  }

  public static Optional<Attachment> forPointer(SignalServiceDataMessage.Quote.QuotedAttachment pointer) {
    SignalServiceAttachment thumbnail = pointer.getThumbnail();

    return Optional.of(new PointerAttachment(pointer.getContentType(),
                                             AttachmentTable.TRANSFER_PROGRESS_PENDING,
                                             thumbnail != null ? thumbnail.asPointer().getSize().orElse(0) : 0,
                                             pointer.getFileName(),
                                             thumbnail != null ? thumbnail.asPointer().getCdnNumber() : 0,
                                             thumbnail != null ? thumbnail.asPointer().getRemoteId().toString() : "0",
                                             thumbnail != null && thumbnail.asPointer().getKey() != null ? Base64.encodeBytes(thumbnail.asPointer().getKey()) : null,
                                             null,
                                             thumbnail != null ? thumbnail.asPointer().getDigest().orElse(null) : null,
                                             null,
                                             false,
                                             false,
                                             false,
                                             thumbnail != null ? thumbnail.asPointer().getWidth() : 0,
                                             thumbnail != null ? thumbnail.asPointer().getHeight() : 0,
                                             thumbnail != null ? thumbnail.asPointer().getUploadTimestamp() : 0,
                                             thumbnail != null ? thumbnail.asPointer().getCaption().orElse(null) : null,
                                             null,
                                             null));
  }
}
