package org.thoughtcrime.securesms.jobs;

import android.Manifest;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.conversation.colors.ChatColorsMapper;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.NotPushRegisteredException;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MultiDeviceContactUpdateJob extends BaseJob {

  public static final String KEY = "MultiDeviceContactUpdateJob";

  private static final String TAG = Log.tag(MultiDeviceContactUpdateJob.class);

  private static final long FULL_SYNC_TIME = TimeUnit.HOURS.toMillis(6);

  private static final String KEY_RECIPIENT  = "recipient";
  private static final String KEY_FORCE_SYNC = "force_sync";

  private @Nullable RecipientId recipientId;

  private boolean forceSync;

  public MultiDeviceContactUpdateJob() {
    this(false);
  }

  public MultiDeviceContactUpdateJob(boolean forceSync) {
    this(null, forceSync);
  }

  public MultiDeviceContactUpdateJob(@Nullable RecipientId recipientId) {
    this(recipientId, true);
  }

  public MultiDeviceContactUpdateJob(@Nullable RecipientId recipientId, boolean forceSync) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("MultiDeviceContactUpdateJob")
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         recipientId,
         forceSync);
  }

  private MultiDeviceContactUpdateJob(@NonNull Job.Parameters parameters, @Nullable RecipientId recipientId, boolean forceSync) {
    super(parameters);

    this.recipientId = recipientId;
    this.forceSync   = forceSync;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_RECIPIENT, recipientId != null ? recipientId.serialize() : null)
                             .putBoolean(KEY_FORCE_SYNC, forceSync)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun()
      throws IOException, UntrustedIdentityException, NetworkException
  {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    if (recipientId == null) generateFullContactUpdate();
    else                     generateSingleContactUpdate(recipientId);
  }

  private void generateSingleContactUpdate(@NonNull RecipientId recipientId)
      throws IOException, UntrustedIdentityException, NetworkException
  {
    WriteDetails writeDetails = createTempFile();

    try {
      DeviceContactsOutputStream out       = new DeviceContactsOutputStream(writeDetails.outputStream);
      Recipient                  recipient = Recipient.resolved(recipientId);

      if (recipient.getRegistered() == RecipientDatabase.RegisteredState.NOT_REGISTERED) {
        Log.w(TAG, recipientId + " not registered!");
        return;
      }

      Optional<IdentityRecord>  identityRecord  = ApplicationDependencies.getIdentityStore().getIdentityRecord(recipient.getId());
      Optional<VerifiedMessage> verifiedMessage = getVerifiedMessage(recipient, identityRecord);
      Map<RecipientId, Integer> inboxPositions  = SignalDatabase.threads().getInboxPositions();
      Set<RecipientId>          archived        = SignalDatabase.threads().getArchivedRecipients();

      out.write(new DeviceContact(RecipientUtil.toSignalServiceAddress(context, recipient),
                                  Optional.fromNullable(recipient.isGroup() || recipient.isSystemContact() ? recipient.getDisplayName(context) : null),
                                  getAvatar(recipient.getId(), recipient.getContactUri()),
                                  Optional.of(ChatColorsMapper.getMaterialColor(recipient.getChatColors()).serialize()),
                                  verifiedMessage,
                                  ProfileKeyUtil.profileKeyOptional(recipient.getProfileKey()),
                                  recipient.isBlocked(),
                                  recipient.getExpiresInSeconds() > 0 ? Optional.of(recipient.getExpiresInSeconds())
                                                                      : Optional.absent(),
                                  Optional.fromNullable(inboxPositions.get(recipientId)),
                                  archived.contains(recipientId)));

      out.close();

      long length = BlobProvider.getInstance().calculateFileSize(context, writeDetails.uri);

      sendUpdate(ApplicationDependencies.getSignalServiceMessageSender(),
                 BlobProvider.getInstance().getStream(context, writeDetails.uri),
                 length,
                 false);

    } catch(InvalidNumberException e) {
      Log.w(TAG, e);
    } finally {
      BlobProvider.getInstance().delete(context, writeDetails.uri);
    }
  }

  private void generateFullContactUpdate()
      throws IOException, UntrustedIdentityException, NetworkException
  {
    boolean isAppVisible      = ApplicationDependencies.getAppForegroundObserver().isForegrounded();
    long    timeSinceLastSync = System.currentTimeMillis() - TextSecurePreferences.getLastFullContactSyncTime(context);

    Log.d(TAG, "Requesting a full contact sync. forced = " + forceSync + ", appVisible = " + isAppVisible + ", timeSinceLastSync = " + timeSinceLastSync + " ms");

    if (!forceSync && !isAppVisible && timeSinceLastSync < FULL_SYNC_TIME) {
      Log.i(TAG, "App is backgrounded and the last contact sync was too soon (" + timeSinceLastSync + " ms ago). Marking that we need a sync. Skipping multi-device contact update...");
      TextSecurePreferences.setNeedsFullContactSync(context, true);
      return;
    }

    TextSecurePreferences.setLastFullContactSyncTime(context, System.currentTimeMillis());
    TextSecurePreferences.setNeedsFullContactSync(context, false);

    WriteDetails writeDetails = createTempFile();

    try {
      DeviceContactsOutputStream out            = new DeviceContactsOutputStream(writeDetails.outputStream);
      List<Recipient>            recipients     = SignalDatabase.recipients().getRecipientsForMultiDeviceSync();
      Map<RecipientId, Integer>  inboxPositions = SignalDatabase.threads().getInboxPositions();
      Set<RecipientId>           archived       = SignalDatabase.threads().getArchivedRecipients();

      for (Recipient recipient : recipients) {
        Optional<IdentityRecord>  identity      = ApplicationDependencies.getIdentityStore().getIdentityRecord(recipient.getId());
        Optional<VerifiedMessage> verified      = getVerifiedMessage(recipient, identity);
        Optional<String>          name          = Optional.fromNullable(recipient.isSystemContact() ? recipient.getDisplayName(context) : recipient.getGroupName(context));
        Optional<ProfileKey>      profileKey    = ProfileKeyUtil.profileKeyOptional(recipient.getProfileKey());
        boolean                   blocked       = recipient.isBlocked();
        Optional<Integer>         expireTimer   = recipient.getExpiresInSeconds() > 0 ? Optional.of(recipient.getExpiresInSeconds()) : Optional.absent();
        Optional<Integer>         inboxPosition = Optional.fromNullable(inboxPositions.get(recipient.getId()));

        out.write(new DeviceContact(RecipientUtil.toSignalServiceAddress(context, recipient),
                                    name,
                                    getAvatar(recipient.getId(), recipient.getContactUri()),
                                    Optional.of(ChatColorsMapper.getMaterialColor(recipient.getChatColors()).serialize()),
                                    verified,
                                    profileKey,
                                    blocked,
                                    expireTimer,
                                    inboxPosition,
                                    archived.contains(recipient.getId())));
      }


      Recipient self       = Recipient.self();
      byte[]    profileKey = self.getProfileKey();

      if (profileKey != null) {
        out.write(new DeviceContact(RecipientUtil.toSignalServiceAddress(context, self),
                                    Optional.absent(),
                                    Optional.absent(),
                                    Optional.of(ChatColorsMapper.getMaterialColor(self.getChatColors()).serialize()),
                                    Optional.absent(),
                                    ProfileKeyUtil.profileKeyOptionalOrThrow(self.getProfileKey()),
                                    false,
                                    self.getExpiresInSeconds() > 0 ? Optional.of(self.getExpiresInSeconds()) : Optional.absent(),
                                    Optional.fromNullable(inboxPositions.get(self.getId())),
                                    archived.contains(self.getId())));
      }

      out.close();

      long length = BlobProvider.getInstance().calculateFileSize(context, writeDetails.uri);

      sendUpdate(ApplicationDependencies.getSignalServiceMessageSender(),
                 BlobProvider.getInstance().getStream(context, writeDetails.uri),
                 length,
                 true);
    } catch(InvalidNumberException e) {
      Log.w(TAG, e);
    } finally {
      BlobProvider.getInstance().delete(context, writeDetails.uri);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof ServerRejectedException) return false;
    return exception instanceof PushNetworkException ||
           exception instanceof NetworkException;
  }

  @Override
  public void onFailure() {

  }

  private void sendUpdate(SignalServiceMessageSender messageSender, InputStream stream, long length, boolean complete)
      throws UntrustedIdentityException, NetworkException
  {
    if (length > 0) {
      try {
        SignalServiceAttachmentStream.Builder attachmentStream = SignalServiceAttachment.newStreamBuilder()
                                                                                        .withStream(stream)
                                                                                        .withContentType("application/octet-stream")
                                                                                        .withLength(length)
                                                                                        .withResumableUploadSpec(messageSender.getResumableUploadSpec());

        messageSender.sendSyncMessage(SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream.build(), complete)),
                                      UnidentifiedAccessUtil.getAccessForSync(context));
      } catch (IOException ioe) {
        throw new NetworkException(ioe);
      }
    } else {
      Log.w(TAG, "Nothing to write!");
    }
  }

  private Optional<SignalServiceAttachmentStream> getAvatar(@NonNull RecipientId recipientId, @Nullable Uri uri) {
    Optional<SignalServiceAttachmentStream> stream;

    if (SignalStore.settings().isPreferSystemContactPhotos()) {
      stream = getSystemAvatar(uri);

      if (!stream.isPresent()) {
        stream = getProfileAvatar(recipientId);
      }
    } else {
      stream = getProfileAvatar(recipientId);

      if (!stream.isPresent()) {
        stream = getSystemAvatar(uri);
      }
    }

    return stream;
  }

  private Optional<SignalServiceAttachmentStream> getProfileAvatar(@NonNull RecipientId recipientId) {
    if (AvatarHelper.hasAvatar(context, recipientId)) {
      try {
        long length = AvatarHelper.getAvatarLength(context, recipientId);
        return Optional.of(SignalServiceAttachmentStream.newStreamBuilder()
                                                        .withStream(AvatarHelper.getAvatar(context, recipientId))
                                                        .withContentType("image/*")
                                                        .withLength(length)
                                                        .build());
      } catch (IOException e) {
        Log.w(TAG, "Failed to read profile avatar!", e);
        return Optional.absent();
      }
    }

    return Optional.absent();
  }

  private Optional<SignalServiceAttachmentStream> getSystemAvatar(@Nullable Uri uri) {
    if (uri == null) {
      return Optional.absent();
    }

    if (!Permissions.hasAny(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
      return Optional.absent();
    }

    Uri displayPhotoUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO);

    try {
      AssetFileDescriptor fd = context.getContentResolver().openAssetFileDescriptor(displayPhotoUri, "r");

      if (fd == null) {
        return Optional.absent();
      }

      return Optional.of(SignalServiceAttachment.newStreamBuilder()
                                                .withStream(fd.createInputStream())
                                                .withContentType("image/*")
                                                .withLength(fd.getLength())
                                                .build());
    } catch (IOException e) {
      // Ignored
    }

    Uri photoUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);

    if (photoUri == null) {
      return Optional.absent();
    }

    Cursor cursor = context.getContentResolver().query(photoUri,
                                                       new String[] {
                                                           ContactsContract.CommonDataKinds.Photo.PHOTO,
                                                           ContactsContract.CommonDataKinds.Phone.MIMETYPE
                                                       }, null, null, null);

    try {
      if (cursor != null && cursor.moveToNext()) {
        byte[] data = cursor.getBlob(0);

        if (data != null) {
          return Optional.of(SignalServiceAttachment.newStreamBuilder()
                                                    .withStream(new ByteArrayInputStream(data))
                                                    .withContentType("image/*")
                                                    .withLength(data.length)
                                                    .build());
        }
      }

      return Optional.absent();
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  private Optional<VerifiedMessage> getVerifiedMessage(Recipient recipient, Optional<IdentityRecord> identity)
      throws InvalidNumberException, IOException
  {
    if (!identity.isPresent()) return Optional.absent();

    SignalServiceAddress destination = RecipientUtil.toSignalServiceAddress(context, recipient);
    IdentityKey          identityKey = identity.get().getIdentityKey();

    VerifiedMessage.VerifiedState state;

    switch (identity.get().getVerifiedStatus()) {
      case VERIFIED:   state = VerifiedMessage.VerifiedState.VERIFIED;   break;
      case UNVERIFIED: state = VerifiedMessage.VerifiedState.UNVERIFIED; break;
      case DEFAULT:    state = VerifiedMessage.VerifiedState.DEFAULT;    break;
      default: throw new AssertionError("Unknown state: " + identity.get().getVerifiedStatus());
    }

    return Optional.of(new VerifiedMessage(destination, identityKey, state, System.currentTimeMillis()));
  }

  private @NonNull WriteDetails createTempFile() throws IOException {
    ParcelFileDescriptor[] pipe        = ParcelFileDescriptor.createPipe();
    InputStream            inputStream = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
    Uri                    uri         = BlobProvider.getInstance()
                                                     .forData(inputStream, 0)
                                                     .withFileName("multidevice-contact-update")
                                                     .createForSingleSessionOnDiskAsync(context,
                                                                                        () -> Log.i(TAG, "Write successful."),
                                                                                        e  -> Log.w(TAG, "Error during write.", e));

    return new WriteDetails(uri, new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]));
  }

  private static class NetworkException extends Exception {

    public NetworkException(Exception ioe) {
      super(ioe);
    }
  }

  private static class WriteDetails {
    private final Uri          uri;
    private final OutputStream outputStream;

    private WriteDetails(@NonNull Uri uri, @NonNull OutputStream outputStream) {
      this.uri          = uri;
      this.outputStream = outputStream;
    }
  }

  public static final class Factory implements Job.Factory<MultiDeviceContactUpdateJob> {
    @Override
    public @NonNull MultiDeviceContactUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      String      serialized = data.getString(KEY_RECIPIENT);
      RecipientId address    = serialized != null ? RecipientId.from(serialized) : null;

      return new MultiDeviceContactUpdateJob(parameters, address, data.getBoolean(KEY_FORCE_SYNC));
    }
  }
}
