package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.support.annotation.Nullable;

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
    TextSecureMessageSender messageSender   = messageSenderFactory.create(masterSecret);
    File                    contactDataFile = createTempFile("multidevice-contact-update");
    GroupDatabase.Reader    reader          = null;

    GroupDatabase.GroupRecord record;

    try {
      DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(new FileOutputStream(contactDataFile));

      reader = DatabaseFactory.getGroupDatabase(context).getGroups();

      while ((record = reader.getNext()) != null) {
        out.write(new DeviceGroup(record.getId(), Optional.fromNullable(record.getTitle()),
                                  record.getMembers(), getAvatar(record.getAvatar())));
      }

      out.close();

      sendUpdate(messageSender, contactDataFile);

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
    TextSecureAttachmentStream attachmentStream   = new TextSecureAttachmentStream(contactsFileStream,
                                                                                   "application/octet-stream",
                                                                                   contactsFile.length());

    messageSender.sendMessage(TextSecureSyncMessage.forGroups(attachmentStream));
  }


  private Optional<TextSecureAttachmentStream> getAvatar(@Nullable byte[] avatar) {
    if (avatar == null) return Optional.absent();

    return Optional.of(new TextSecureAttachmentStream(new ByteArrayInputStream(avatar),
                                                      "image/*", avatar.length));
  }

  private File createTempFile(String prefix) throws IOException {
    File file = File.createTempFile(prefix, "tmp", context.getCacheDir());
    file.deleteOnExit();

    return file;
  }


}
