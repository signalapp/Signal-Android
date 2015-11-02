package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment.ProgressListener;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import de.greenrobot.event.EventBus;
import ws.com.google.android.mms.ContentType;

public abstract class PushSendJob extends SendJob {

  private static final String TAG = PushSendJob.class.getSimpleName();

  protected PushSendJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  protected static JobParameters constructParameters(Context context, String destination) {
    JobParameters.Builder builder = JobParameters.newBuilder();
    builder.withPersistence();
    builder.withGroupId(destination);
    builder.withRequirement(new MasterSecretRequirement(context));
    builder.withRequirement(new NetworkRequirement(context));
    builder.withRetryCount(5);

    return builder.create();
  }

  protected TextSecureAddress getPushAddress(String number) throws InvalidNumberException {
    String e164number = Util.canonicalizeNumber(context, number);
    String relay      = TextSecureDirectory.getInstance(context).getRelay(e164number);
    return new TextSecureAddress(e164number, Optional.fromNullable(relay));
  }

  protected List<TextSecureAttachment> getAttachmentsFor(MasterSecret masterSecret, List<Attachment> parts) {
    List<TextSecureAttachment> attachments = new LinkedList<>();

    for (final Attachment attachment : parts) {
      if (ContentType.isImageType(attachment.getContentType()) ||
          ContentType.isAudioType(attachment.getContentType()) ||
          ContentType.isVideoType(attachment.getContentType()))
      {
        try {
          if (attachment.getDataUri() == null) throw new IOException("Assertion failed, outgoing attachment has no data!");
          InputStream is = PartAuthority.getAttachmentStream(context, masterSecret, attachment.getDataUri());
          attachments.add(TextSecureAttachment.newStreamBuilder()
                                              .withStream(is)
                                              .withContentType(attachment.getContentType())
                                              .withLength(attachment.getSize())
                                              .withListener(new ProgressListener() {
                                                @Override
                                                public void onAttachmentProgress(long total, long progress) {
                                                  EventBus.getDefault().postSticky(new PartProgressEvent(attachment, total, progress));
                                                }
                                              })
                                              .build());
        } catch (IOException ioe) {
          Log.w(TAG, "Couldn't open attachment", ioe);
        }
      }
    }

    return attachments;
  }

  protected void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long       threadId   = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId);
    Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);

    if (threadId != -1 && recipients != null) {
      MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
    }
  }
}
