package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingPartDatabase;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.push.TextSecureCommunicationFactory;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentPointer;
import org.whispersystems.textsecure.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.textsecure.push.exceptions.PushNetworkException;
import org.whispersystems.textsecure.util.Base64;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduPart;

public class AttachmentDownloadJob extends MasterSecretJob {

  private static final String TAG = AttachmentDownloadJob.class.getSimpleName();

  private final long messageId;

  public AttachmentDownloadJob(Context context, long messageId) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .create());

    this.messageId = messageId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws RequirementNotMetException, IOException {
    MasterSecret masterSecret = getMasterSecret();
    PartDatabase database     = DatabaseFactory.getEncryptingPartDatabase(context, masterSecret);

    Log.w(TAG, "Downloading push parts for: " + messageId);

    List<Pair<Long, PduPart>> parts = database.getParts(messageId, false);

    for (Pair<Long, PduPart> partPair : parts) {
      retrievePart(masterSecret, partPair.second, messageId, partPair.first);
      Log.w(TAG, "Got part: " + partPair.first);
    }
  }

  @Override
  public void onCanceled() {
    try {
      MasterSecret              masterSecret = getMasterSecret();
      PartDatabase              database     = DatabaseFactory.getEncryptingPartDatabase(context, masterSecret);
      List<Pair<Long, PduPart>> parts        = database.getParts(messageId, false);

      for (Pair<Long, PduPart> partPair : parts) {
        markFailed(masterSecret, messageId, partPair.second, partPair.first);
      }
    } catch (RequirementNotMetException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    if (throwable instanceof PushNetworkException)       return true;
    if (throwable instanceof RequirementNotMetException) return true;

    return false;
  }

  private void retrievePart(MasterSecret masterSecret, PduPart part, long messageId, long partId)
      throws IOException
  {
    TextSecureMessageReceiver receiver       = TextSecureCommunicationFactory.createReceiver(context, masterSecret);
    EncryptingPartDatabase    database       = DatabaseFactory.getEncryptingPartDatabase(context, masterSecret);
    File                      attachmentFile = null;

    try {
      attachmentFile = createTempFile();

      TextSecureAttachmentPointer pointer    = createAttachmentPointer(masterSecret, part);
      InputStream                 attachment = receiver.retrieveAttachment(pointer, attachmentFile);

      database.updateDownloadedPart(messageId, partId, part, attachment);
    } catch (InvalidPartException | NonSuccessfulResponseCodeException | InvalidMessageException | MmsException e) {
      Log.w(TAG, e);
      markFailed(masterSecret, messageId, part, partId);
    } finally {
      if (attachmentFile != null)
        attachmentFile.delete();
    }
  }

  private TextSecureAttachmentPointer createAttachmentPointer(MasterSecret masterSecret, PduPart part)
      throws InvalidPartException
  {
    try {
      MasterCipher masterCipher = new MasterCipher(masterSecret);
      long         id           = Long.parseLong(Util.toIsoString(part.getContentLocation()));
      byte[]       key          = masterCipher.decryptBytes(Base64.decode(Util.toIsoString(part.getContentDisposition())));
      String       relay        = null;

      if (part.getName() != null) {
        relay = Util.toIsoString(part.getName());
      }

      return new TextSecureAttachmentPointer(id, null, key, relay);
    } catch (InvalidMessageException | IOException e) {
      Log.w(TAG, e);
      throw new InvalidPartException(e);
    }
  }

  private File createTempFile() throws InvalidPartException {
    try {
      File file = File.createTempFile("push-attachment", "tmp");
      file.deleteOnExit();

      return file;
    } catch (IOException e) {
      throw new InvalidPartException(e);
    }
  }

  private void markFailed(MasterSecret masterSecret, long messageId, PduPart part, long partId) {
    try {
      EncryptingPartDatabase database = DatabaseFactory.getEncryptingPartDatabase(context, masterSecret);
      database.updateFailedDownloadedPart(messageId, partId, part);
    } catch (MmsException e) {
      Log.w(TAG, e);
    }
  }

  private class InvalidPartException extends Exception {
    public InvalidPartException(Exception e) {super(e);}
  }
}
