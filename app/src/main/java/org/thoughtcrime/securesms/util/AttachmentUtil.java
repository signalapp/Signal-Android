package org.thoughtcrime.securesms.util;


import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.jobmanager.impl.NotInCallConstraint;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Collections;
import java.util.Set;

public class AttachmentUtil {

  private static final String TAG = Log.tag(AttachmentUtil.class);

  @WorkerThread
  public static boolean isAutoDownloadPermitted(@NonNull Context context, @Nullable DatabaseAttachment attachment) {
    if (attachment == null) {
      Log.w(TAG, "attachment was null, returning vacuous true");
      return true;
    }

    if (!isFromTrustedConversation(context, attachment)) {
      Log.w(TAG, "Not allowing download due to untrusted conversation");
      return false;
    }

    Set<String> allowedTypes = getAllowedAutoDownloadTypes(context);
    String      contentType  = attachment.contentType;

    if (attachment.voiceNote ||
        (MediaUtil.isAudio(attachment) && TextUtils.isEmpty(attachment.fileName)) ||
        MediaUtil.isLongTextType(attachment.contentType) ||
        attachment.isSticker())
    {
      return true;
    } else if (attachment.videoGif) {
      boolean allowed = NotInCallConstraint.isNotInConnectedCall() && allowedTypes.contains("image");
      if (!allowed) {
        Log.w(TAG, "Not auto downloading. inCall: " + NotInCallConstraint.isNotInConnectedCall() + " allowedType: " + allowedTypes.contains("image"));
      }
      return allowed;
    } else if (isNonDocumentType(contentType)) {
      boolean allowed = NotInCallConstraint.isNotInConnectedCall() && allowedTypes.contains(MediaUtil.getDiscreteMimeType(contentType));
      if (!allowed) {
        Log.w(TAG, "Not auto downloading. inCall: " + NotInCallConstraint.isNotInConnectedCall() + " allowedType: " + allowedTypes.contains(MediaUtil.getDiscreteMimeType(contentType)));
      }
      return allowed;
    } else {
      boolean allowed = NotInCallConstraint.isNotInConnectedCall() && allowedTypes.contains("documents");
      if (!allowed) {
        Log.w(TAG, "Not auto downloading. inCall: " + NotInCallConstraint.isNotInConnectedCall() + " allowedType: " + allowedTypes.contains("documents"));
      }
      return allowed;
    }
  }

  /**
   * Deletes the specified attachment. If its the only attachment for its linked message, the entire
   * message is deleted.
   */
  @WorkerThread
  public static void deleteAttachment(@NonNull Context context,
                                      @NonNull DatabaseAttachment attachment)
  {
    AttachmentId attachmentId    = attachment.attachmentId;
    long         mmsId           = attachment.mmsId;
    int          attachmentCount = SignalDatabase.attachments()
                                                 .getAttachmentsForMessage(mmsId)
                                                 .size();

    if (attachmentCount <= 1) {
      SignalDatabase.messages().deleteMessage(mmsId);
    } else {
      SignalDatabase.attachments().deleteAttachment(attachmentId);
    }
  }

  private static boolean isNonDocumentType(String contentType) {
    return
        MediaUtil.isImageType(contentType) ||
        MediaUtil.isVideoType(contentType) ||
        MediaUtil.isAudioType(contentType);
  }

  private static @NonNull Set<String> getAllowedAutoDownloadTypes(@NonNull Context context) {
    if      (NetworkUtil.isConnectedWifi(context))    return TextSecurePreferences.getWifiMediaDownloadAllowed(context);
    else if (NetworkUtil.isConnectedRoaming(context)) return TextSecurePreferences.getRoamingMediaDownloadAllowed(context);
    else if (NetworkUtil.isConnectedMobile(context))  return TextSecurePreferences.getMobileMediaDownloadAllowed(context);
    else                                              return Collections.emptySet();
  }

  @WorkerThread
  private static boolean isFromTrustedConversation(@NonNull Context context, @NonNull DatabaseAttachment attachment) {
    try {
      MessageRecord message = SignalDatabase.messages().getMessageRecord(attachment.mmsId);

      Recipient fromRecipient = message.getFromRecipient();
      Recipient toRecipient   = SignalDatabase.threads().getRecipientForThreadId(message.getThreadId());

      if (toRecipient != null && toRecipient.isGroup()) {
        return toRecipient.isProfileSharing() || isTrustedIndividual(fromRecipient, message);
      } else {
        return isTrustedIndividual(fromRecipient, message);
      }
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "Message could not be found! Assuming not a trusted contact.");
      return false;
    }
  }

  private static boolean isTrustedIndividual(@NonNull Recipient recipient, @NonNull MessageRecord message) {
    return recipient.isSystemContact()  ||
           recipient.isProfileSharing() ||
           message.isOutgoing()         ||
           recipient.isSelf()           ||
           recipient.isReleaseNotes();
    }
  }
