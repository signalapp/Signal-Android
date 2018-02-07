package org.thoughtcrime.securesms.attachments;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.util.LinkedList;
import java.util.List;

public class PointerAttachment extends Attachment {

  private PointerAttachment(@NonNull String contentType, int transferState, long size,
                            @Nullable String fileName,  @NonNull String location,
                            @Nullable String key, @NonNull String relay,
                            @Nullable byte[] digest, boolean voiceNote,
                            int width, int height)
  {
    super(contentType, transferState, size, fileName, location, key, relay, digest, null, voiceNote, width, height, false);
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

  public static Optional<Attachment> forPointer(Optional<SignalServiceAttachment> pointer) {
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
                                      encodedKey, pointer.get().asPointer().getRelay().orNull(),
                                      pointer.get().asPointer().getDigest().orNull(),
                                      pointer.get().asPointer().getVoiceNote(),
                                      pointer.get().asPointer().getWidth(),
                                      pointer.get().asPointer().getHeight()));

  }
}
