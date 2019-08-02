package org.thoughtcrime.securesms.revealable;

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;

import java.util.concurrent.TimeUnit;

public class RevealableUtil {

  public static final long MAX_LIFESPAN = TimeUnit.DAYS.toMillis(30);
  public static final long DURATION     = TimeUnit.SECONDS.toMillis(5);

  public static boolean isViewable(@Nullable MmsMessageRecord message) {
    if (message.getRevealDuration() == 0) {
      return true;
    } else if (message.getSlideDeck().getThumbnailSlide() == null) {
      return false;
    } else if (message.getSlideDeck().getThumbnailSlide().getUri() == null) {
      return false;
    } else if (message.isOutgoing() && message.getSlideDeck().getThumbnailSlide().getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_STARTED) {
      return true;
    } else if (message.getSlideDeck().getThumbnailSlide().getTransferState() != AttachmentDatabase.TRANSFER_PROGRESS_DONE) {
      return false;
    } else if (isRevealExpired(message)) {
      return false;
    } else {
      return true;
    }
  }

  public static boolean isRevealExpired(@Nullable MmsMessageRecord message) {
    if (message == null) {
      return false;
    } else if (message.getRevealDuration() == 0) {
      return false;
    } else if (message.getDateReceived() + MAX_LIFESPAN < System.currentTimeMillis()) {
      return true;
    } else if (message.getRevealStartTime() == 0) {
      return false;
    } else if (message.getRevealStartTime() + message.getRevealDuration() < System.currentTimeMillis()) {
      return true;
    } else {
      return false;
    }
  }
}
