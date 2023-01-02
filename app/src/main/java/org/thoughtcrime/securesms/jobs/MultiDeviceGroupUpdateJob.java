package org.thoughtcrime.securesms.jobs;

import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.conversation.colors.ChatColorsMapper;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.NotPushRegisteredException;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MultiDeviceGroupUpdateJob extends BaseJob {

  public static final String KEY = "MultiDeviceGroupUpdateJob";

  private static final String TAG = Log.tag(MultiDeviceGroupUpdateJob.class);

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
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    if (SignalStore.account().isLinkedDevice()) {
      Log.i(TAG, "Not primary device, aborting...");
      return;
    }

    ParcelFileDescriptor[] pipe        = ParcelFileDescriptor.createPipe();
    InputStream            inputStream = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
    Uri                    uri         = BlobProvider.getInstance()
                                                     .forData(inputStream, 0)
                                                     .withFileName("multidevice-group-update")
                                                     .createForSingleSessionOnDiskAsync(context,
                                                                                        () -> Log.i(TAG, "Write successful."),
                                                                                        e  -> Log.w(TAG, "Error during write.", e));

    try (GroupTable.Reader reader = SignalDatabase.groups().getGroups()) {
      DeviceGroupsOutputStream out     = new DeviceGroupsOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]));
      boolean                  hasData = false;

      GroupRecord record;

      while ((record = reader.getNext()) != null) {
        if (record.isV1Group()) {
          List<SignalServiceAddress> members           = new LinkedList<>();
          List<Recipient>            registeredMembers = RecipientUtil.getEligibleForSending(Recipient.resolvedList(record.getMembers()));

          for (Recipient member : registeredMembers) {
            members.add(RecipientUtil.toSignalServiceAddress(context, member));
          }

          RecipientId               recipientId     = SignalDatabase.recipients().getOrInsertFromPossiblyMigratedGroupId(record.getId());
          Recipient                 recipient       = Recipient.resolved(recipientId);
          Optional<Integer>         expirationTimer = recipient.getExpiresInSeconds() > 0 ? Optional.of(recipient.getExpiresInSeconds()) : Optional.empty();
          Map<RecipientId, Integer> inboxPositions  = SignalDatabase.threads().getInboxPositions();
          Set<RecipientId>          archived        = SignalDatabase.threads().getArchivedRecipients();

          out.write(new DeviceGroup(record.getId().getDecodedId(),
                                    Optional.ofNullable(record.getTitle()),
                                    members,
                                    getAvatar(record.getRecipientId()),
                                    record.isActive(),
                                    expirationTimer,
                                    Optional.of(ChatColorsMapper.getMaterialColor(recipient.getChatColors()).serialize()),
                                    recipient.isBlocked(),
                                    Optional.ofNullable(inboxPositions.get(recipientId)),
                                    archived.contains(recipientId)));

          hasData = true;
        }
      }

      out.close();

      if (hasData) {
        long length = BlobProvider.getInstance().calculateFileSize(context, uri);

        sendUpdate(ApplicationDependencies.getSignalServiceMessageSender(),
                   BlobProvider.getInstance().getStream(context, uri),
                   length);
      } else {
        Log.w(TAG, "No groups present for sync message. Sending an empty update.");

        sendUpdate(ApplicationDependencies.getSignalServiceMessageSender(),
                   null,
                   0);
      }
    } finally {
      BlobProvider.getInstance().delete(context, uri);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof ServerRejectedException) return false;
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onFailure() {

  }

  private void sendUpdate(SignalServiceMessageSender messageSender, InputStream stream, long length)
      throws IOException, UntrustedIdentityException
  {
    SignalServiceAttachmentStream attachmentStream;

    if (length > 0) {
      attachmentStream = SignalServiceAttachment.newStreamBuilder()
                                                .withStream(stream)
                                                .withContentType("application/octet-stream")
                                                .withLength(length)
                                                .build();
    } else {
      attachmentStream = SignalServiceAttachment.emptyStream("application/octet-stream");
    }

    messageSender.sendSyncMessage(SignalServiceSyncMessage.forGroups(attachmentStream),
                                  UnidentifiedAccessUtil.getAccessForSync(context));
  }


  private Optional<SignalServiceAttachmentStream> getAvatar(@NonNull RecipientId recipientId) throws IOException {
    if (!AvatarHelper.hasAvatar(context, recipientId)) return Optional.empty();

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
