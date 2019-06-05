package org.thoughtcrime.securesms.attachments;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.LinkedList;
import java.util.List;

public class PointerAttachment extends Attachment {

  private PointerAttachment(@NonNull String contentType, int transferState, long size,
                            @Nullable String fileName,  @NonNull String location,
                            @Nullable String key, @Nullable String relay,
                            @Nullable byte[] digest, @Nullable String fastPreflightId, boolean voiceNote,
                            int width, int height, @Nullable String caption, @Nullable StickerLocator stickerLocator)
  {
    super(contentType, transferState, size, fileName, location, key, relay, digest, fastPreflightId, voiceNote, width, height, false, caption, stickerLocator);
  }

  @Nullable
  @Override
  public Uri getDataUri() {
    return null;
  }

  @Nullable
  @Override
  public Uri getThumbnailUri() {
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
    if (!pointer.isPresent() || !pointer.get().isPointer()) return Optional.absent();

    String encodedKey = null;

    if (pointer.get().asPointer().getKey() != null) {
      encodedKey = Base64.encodeBytes(pointer.get().asPointer().getKey());
    }

    return Optional.of(new PointerAttachment(pointer.get().getContentType(),
                                      AttachmentDatabase.TRANSFER_PROGRESS_PENDING,
                                      pointer.get().asPointer().getSize().or(0),
                                      pointer.get().asPointer().getFileName().orNull(),
                                      String.valueOf(pointer.get().asPointer().getId()),
                                      encodedKey, null,
                                      pointer.get().asPointer().getDigest().orNull(),
                                      fastPreflightId,
                                      pointer.get().asPointer().getVoiceNote(),
                                      pointer.get().asPointer().getWidth(),
                                      pointer.get().asPointer().getHeight(),
                                      pointer.get().asPointer().getCaption().orNull(),
        stickerLocator));

  }

  public static Optional<Attachment> forPointer(SignalServiceDataMessage.Quote.QuotedAttachment pointer) {
    SignalServiceAttachment thumbnail = pointer.getThumbnail();

    return Optional.of(new PointerAttachment(pointer.getContentType(),
                                             AttachmentDatabase.TRANSFER_PROGRESS_PENDING,
                                             thumbnail != null ? thumbnail.asPointer().getSize().or(0) : 0,
                                             pointer.getFileName(),
                                             String.valueOf(thumbnail != null ? thumbnail.asPointer().getId() : 0),
                                             thumbnail != null && thumbnail.asPointer().getKey() != null ? Base64.encodeBytes(thumbnail.asPointer().getKey()) : null,
                                             null,
                                             thumbnail != null ? thumbnail.asPointer().getDigest().orNull() : null,
                                             null,
                                             false,
                                             thumbnail != null ? thumbnail.asPointer().getWidth() : 0,
                                             thumbnail != null ? thumbnail.asPointer().getHeight() : 0,
                                             thumbnail != null ? thumbnail.asPointer().getCaption().orNull() : null,
                                             null));
  }
}
