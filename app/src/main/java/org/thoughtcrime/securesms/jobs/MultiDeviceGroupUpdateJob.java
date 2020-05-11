package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MultiDeviceGroupUpdateJob extends BaseJob {

  public static final String KEY = "MultiDeviceGroupUpdateJob";

  private static final String TAG = MultiDeviceGroupUpdateJob.class.getSimpleName();

  public MultiDeviceGroupUpdateJob() {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("MultiDeviceGroupUpdateJob")
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build());
  }

  private MultiDeviceGroupUpdateJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public void onRun() throws Exception {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    File                 contactDataFile = createTempFile("multidevice-contact-update");
    GroupDatabase.Reader reader          = null;

    GroupDatabase.GroupRecord record;

    try {
      DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(new FileOutputStream(contactDataFile));

      reader = DatabaseFactory.getGroupDatabase(context).getGroups();

      while ((record = reader.getNext()) != null) {
        if (!record.isMms()) {
          List<SignalServiceAddress> members = new LinkedList<>();

          for (RecipientId member : record.getMembers()) {
            members.add(RecipientUtil.toSignalServiceAddress(context, Recipient.resolved(member)));
          }

          RecipientId               recipientId     = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(record.getId());
          Recipient                 recipient       = Recipient.resolved(recipientId);
          Optional<Integer>         expirationTimer = recipient.getExpireMessages() > 0 ? Optional.of(recipient.getExpireMessages()) : Optional.absent();
          Map<RecipientId, Integer> inboxPositions  = DatabaseFactory.getThreadDatabase(context).getInboxPositions();
          Set<RecipientId>          archived        = DatabaseFactory.getThreadDatabase(context).getArchivedRecipients();

          out.write(new DeviceGroup(record.getId().getDecodedId(),
                                    Optional.fromNullable(record.getTitle()),
                                    members,
                                    getAvatar(record.getRecipientId()),
                                    record.isActive(),
                                    expirationTimer,
                                    Optional.of(recipient.getColor().serialize()),
                                    recipient.isBlocked(),
                                    Optional.fromNullable(inboxPositions.get(recipientId)),
                                    archived.contains(recipientId)));
        }
      }

      out.close();

      if (contactDataFile.exists() && contactDataFile.length() > 0) {
        sendUpdate(ApplicationDependencies.getSignalServiceMessageSender(), contactDataFile);
      } else {
        Log.w(TAG, "No groups present for sync message...");
      }

    } finally {
      if (contactDataFile != null) contactDataFile.delete();
      if (reader != null)          reader.close();
    }

  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onFailure() {

  }

  private void sendUpdate(SignalServiceMessageSender messageSender, File contactsFile)
      throws IOException, UntrustedIdentityException
  {
    FileInputStream               contactsFileStream = new FileInputStream(contactsFile);
    SignalServiceAttachmentStream attachmentStream   = SignalServiceAttachment.newStreamBuilder()
                                                                              .withStream(contactsFileStream)
                                                                              .withContentType("application/octet-stream")
                                                                              .withLength(contactsFile.length())
                                                                              .build();

    messageSender.sendMessage(SignalServiceSyncMessage.forGroups(attachmentStream),
                              UnidentifiedAccessUtil.getAccessForSync(context));
  }


  private Optional<SignalServiceAttachmentStream> getAvatar(@NonNull RecipientId recipientId) throws IOException {
    if (!AvatarHelper.hasAvatar(context, recipientId)) return Optional.absent();

    return Optional.of(SignalServiceAttachment.newStreamBuilder()
                                              .withStream(AvatarHelper.getAvatar(context, recipientId))
                                              .withContentType("image/*")
                                              .withLength(AvatarHelper.getAvatarLength(context, recipientId))
                                              .build());
  }

  private File createTempFile(String prefix) throws IOException {
    File file = File.createTempFile(prefix, "tmp", context.getCacheDir());
    file.deleteOnExit();

    return file;
  }

  public static final class Factory implements Job.Factory<MultiDeviceGroupUpdateJob> {
    @Override
    public @NonNull MultiDeviceGroupUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MultiDeviceGroupUpdateJob(parameters);
    }
  }
}
