package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.TextSecureCommunicationModule;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentStream;
import org.whispersystems.textsecure.api.messages.multidevice.DeviceGroup;
import org.whispersystems.textsecure.api.messages.multidevice.DeviceGroupsOutputStream;
import org.whispersystems.textsecure.api.messages.multidevice.TextSecureSyncMessage;
import org.whispersystems.textsecure.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.inject.Inject;

public class MultiDeviceGroupUpdateJob extends MasterSecretJob implements InjectableType {

  private static final long serialVersionUID = 1L;
  private static final String TAG = MultiDeviceGroupUpdateJob.class.getSimpleName();

  @Inject
  transient TextSecureCommunicationModule.TextSecureMessageSenderFactory messageSenderFactory;

  public MultiDeviceGroupUpdateJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withRequirement(new MasterSecretRequirement(context))
                                .withGroupId(MultiDeviceGroupUpdateJob.class.getSimpleName())
                                .withPersistence()
                                .create());
  }

  @Override
  public void onRun(MasterSecret masterSecret) throws Exception {
    TextSecureMessageSender messageSender   = messageSenderFactory.create();
    File                    contactDataFile = createTempFile("multidevice-contact-update");
    GroupDatabase.Reader    reader          = null;

    GroupDatabase.GroupRecord record;

    try {
      DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(new FileOutputStream(contactDataFile));

      reader = DatabaseFactory.getGroupDatabase(context).getGroups();

      while ((record = reader.getNext()) != null) {
        out.write(new DeviceGroup(record.getId(), Optional.fromNullable(record.getTitle()),
                                  record.getMembers(), getAvatar(record.getAvatar()),
                                  record.isActive()));
      }

      out.close();

      if (contactDataFile.exists() && contactDataFile.length() > 0) {
        sendUpdate(messageSender, contactDataFile);
      } else {
        Log.w(TAG, "No groups present for sync message...");
      }

    } finally {
      if (contactDataFile != null) contactDataFile.delete();
      if (reader != null)          reader.close();
    }

  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onCanceled() {

  }

  private void sendUpdate(TextSecureMessageSender messageSender, File contactsFile)
      throws IOException, UntrustedIdentityException
  {
    FileInputStream            contactsFileStream = new FileInputStream(contactsFile);
    TextSecureAttachmentStream attachmentStream   = TextSecureAttachment.newStreamBuilder()
                                                                        .withStream(contactsFileStream)
                                                                        .withContentType("application/octet-stream")
                                                                        .withLength(contactsFile.length())
                                                                        .build();

    messageSender.sendMessage(TextSecureSyncMessage.forGroups(attachmentStream));
  }


  private Optional<TextSecureAttachmentStream> getAvatar(@Nullable byte[] avatar) {
    if (avatar == null) return Optional.absent();

    return Optional.of(TextSecureAttachment.newStreamBuilder()
                                           .withStream(new ByteArrayInputStream(avatar))
                                           .withContentType("image/*")
                                           .withLength(avatar.length)
                                           .build());
  }

  private File createTempFile(String prefix) throws IOException {
    File file = File.createTempFile(prefix, "tmp", context.getCacheDir());
    file.deleteOnExit();

    return file;
  }


}
