package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.loki.protocol.SyncMessagesProtocol;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class MultiDeviceContactUpdateJob extends BaseJob implements InjectableType {

  public static final String KEY = "MultiDeviceContactUpdateJob";

  private static final String TAG = MultiDeviceContactUpdateJob.class.getSimpleName();

  private static final long FULL_SYNC_TIME = TimeUnit.HOURS.toMillis(6);

  private static final String KEY_ADDRESS    = "address";
  private static final String KEY_FORCE_SYNC = "force_sync";

  @Inject SignalServiceMessageSender messageSender;

  private @Nullable String address;

  private boolean forceSync;

  /**
   * Create a full contact sync job that syncs to all linked devices.
   */
  public MultiDeviceContactUpdateJob(@NonNull Context context) {
    this(context, false);
  }

  public MultiDeviceContactUpdateJob(@NonNull Context context, boolean forceSync) {
    this(context, null, forceSync);
  }

  /**
   * Create a single contact sync job that syncs `address` to all linked devices.
   */
  public MultiDeviceContactUpdateJob(@NonNull Context context, @Nullable Address address) {
    this(context, address, true);
  }

  public MultiDeviceContactUpdateJob(@NonNull Context context, @Nullable Address address, boolean forceSync) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("MultiDeviceContactUpdateJob")
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(1)
                           .build(),
         address,
         forceSync);
  }

  private MultiDeviceContactUpdateJob(@NonNull Job.Parameters parameters, @Nullable Address address, boolean forceSync) {
    super(parameters);

    this.forceSync = forceSync;

    if (address != null) this.address = address.serialize();
    else                 this.address = null;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_ADDRESS, address)
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
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    if (address == null) generateFullContactUpdate();
    else if (SyncMessagesProtocol.shouldSyncContact(context, Address.fromSerialized(address))) generateSingleContactUpdate(Address.fromSerialized(address));
  }

  private void generateSingleContactUpdate(@NonNull Address address)
      throws IOException, UntrustedIdentityException, NetworkException
  {
    // Loki - Only sync regular contacts
    if (!PublicKeyValidation.isValid(address.serialize())) { return; }

    File contactDataFile = createTempFile("multidevice-contact-update");

    try {
      DeviceContactsOutputStream                out             = new DeviceContactsOutputStream(new FileOutputStream(contactDataFile));
      Recipient                                 recipient       = Recipient.from(context, address, false);
      Optional<IdentityDatabase.IdentityRecord> identityRecord  = DatabaseFactory.getIdentityDatabase(context).getIdentity(address);
      Optional<VerifiedMessage>                 verifiedMessage = getVerifiedMessage(recipient, identityRecord);

      if (SyncMessagesProtocol.shouldSyncContact(context, address)) {
        out.write(new DeviceContact(address.toPhoneString(),
                                    Optional.fromNullable(recipient.getName()),
                                    getAvatar(recipient.getContactUri()),
                                    Optional.fromNullable(recipient.getColor().serialize()),
                                    verifiedMessage,
                                    Optional.fromNullable(recipient.getProfileKey()),
                                    recipient.isBlocked(),
                                    recipient.getExpireMessages() > 0 ? Optional.of(recipient.getExpireMessages()) : Optional.absent()));
      }

      out.close();
      sendUpdate(messageSender, contactDataFile, false);

    } catch(InvalidNumberException e) {
      Log.w(TAG, e);
    } finally {
      if (contactDataFile != null) contactDataFile.delete();
    }
  }

  private void generateFullContactUpdate()
      throws IOException, UntrustedIdentityException, NetworkException
  {
    boolean isAppVisible      = ApplicationContext.getInstance(context).isAppVisible();
    long    timeSinceLastSync = System.currentTimeMillis() - TextSecurePreferences.getLastFullContactSyncTime(context);

    Log.d(TAG, "Requesting a full contact sync. forced = " + forceSync + ", appVisible = " + isAppVisible + ", timeSinceLastSync = " + timeSinceLastSync + " ms");

    if (!forceSync && !isAppVisible && timeSinceLastSync < FULL_SYNC_TIME) {
      Log.i(TAG, "App is backgrounded and the last contact sync was too soon (" + timeSinceLastSync + " ms ago). Marking that we need a sync. Skipping multi-device contact update...");
      TextSecurePreferences.setNeedsFullContactSync(context, true);
      return;
    }

    TextSecurePreferences.setLastFullContactSyncTime(context, System.currentTimeMillis());
    TextSecurePreferences.setNeedsFullContactSync(context, false);

    File contactDataFile = createTempFile("multidevice-contact-update");

    try {
      DeviceContactsOutputStream out = new DeviceContactsOutputStream(new FileOutputStream(contactDataFile));
      List<ContactData> contacts     = SyncMessagesProtocol.getContactsToSync(context);

      for (ContactData contactData : contacts) {
        Uri                                       contactUri  = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(contactData.id));
        Address                                   address     = Address.fromExternal(context, contactData.numbers.get(0).number);
        Recipient                                 recipient   = Recipient.from(context, address, false);
        Optional<IdentityDatabase.IdentityRecord> identity    = DatabaseFactory.getIdentityDatabase(context).getIdentity(address);
        Optional<VerifiedMessage>                 verified    = getVerifiedMessage(recipient, identity);
        Optional<String>                          name        = Optional.fromNullable(contactData.name);
        Optional<String>                          color       = Optional.of(recipient.getColor().serialize());
        Optional<byte[]>                          profileKey  = Optional.fromNullable(recipient.getProfileKey());
        boolean                                   blocked     = recipient.isBlocked();
        Optional<Integer>                         expireTimer = recipient.getExpireMessages() > 0 ? Optional.of(recipient.getExpireMessages()) : Optional.absent();

        out.write(new DeviceContact(address.toPhoneString(), name, getAvatar(contactUri), color, verified, profileKey, blocked, expireTimer));
      }

      if (ProfileKeyUtil.hasProfileKey(context)) {
        Recipient self = Recipient.from(context, Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)), false);
        out.write(new DeviceContact(TextSecurePreferences.getLocalNumber(context),
                                    Optional.absent(), Optional.absent(),
                                    Optional.of(self.getColor().serialize()), Optional.absent(),
                                    Optional.of(ProfileKeyUtil.getProfileKey(context)),
                                    false, self.getExpireMessages() > 0 ? Optional.of(self.getExpireMessages()) : Optional.absent()));
      }

      out.close();
      sendUpdate(messageSender, contactDataFile, true);
    } catch(InvalidNumberException e) {
      Log.w(TAG, e);
    } finally {
      if (contactDataFile != null) contactDataFile.delete();
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    // Loki - Disabled since we have our own retrying
    return false;
  }

  @Override
  public void onCanceled() {

  }

  private void sendUpdate(SignalServiceMessageSender messageSender, File contactsFile, boolean complete)
      throws IOException, UntrustedIdentityException, NetworkException
  {
    if (contactsFile.length() > 0) {
      FileInputStream               contactsFileStream = new FileInputStream(contactsFile);
      SignalServiceAttachmentStream attachmentStream   = SignalServiceAttachment.newStreamBuilder()
                                                                                .withStream(contactsFileStream)
                                                                                .withContentType("application/octet-stream")
                                                                                .withLength(contactsFile.length())
                                                                                .build();

      Optional<UnidentifiedAccessPair> unidentifiedAccess = address != null ? UnidentifiedAccessUtil.getAccessFor(context, Recipient.from(context, Address.fromSerialized(address), false)) : Optional.absent();

      try {
        messageSender.sendMessage(SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, complete)), unidentifiedAccess);
      } catch (IOException ioe) {
        throw new NetworkException(ioe);
      }
    }
  }

  private Optional<SignalServiceAttachmentStream> getAvatar(@Nullable Uri uri) throws IOException {
    return Optional.absent();

    /* Loki - Disabled until we support custom profile pictures. This will then need to be reworked.
    if (uri == null) {
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
      Log.i(TAG, "Could not find avatar for URI: " + displayPhotoUri);
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
     */
  }

  private Optional<VerifiedMessage> getVerifiedMessage(Recipient recipient, Optional<IdentityDatabase.IdentityRecord> identity) throws InvalidNumberException {
    if (!identity.isPresent()) return Optional.absent();

    String      destination = recipient.getAddress().toPhoneString();
    IdentityKey identityKey = identity.get().getIdentityKey();

    VerifiedMessage.VerifiedState state;

    switch (identity.get().getVerifiedStatus()) {
      case VERIFIED:   state = VerifiedMessage.VerifiedState.VERIFIED;   break;
      case UNVERIFIED: state = VerifiedMessage.VerifiedState.UNVERIFIED; break;
      case DEFAULT:    state = VerifiedMessage.VerifiedState.DEFAULT;    break;
      default: throw new AssertionError("Unknown state: " + identity.get().getVerifiedStatus());
    }

    return Optional.of(new VerifiedMessage(destination, identityKey, state, System.currentTimeMillis()));
  }

  private File createTempFile(String prefix) throws IOException {
    File file = File.createTempFile(prefix, "tmp", context.getCacheDir());
    file.deleteOnExit();

    return file;
  }

  private static class NetworkException extends Exception {

    public NetworkException(Exception ioe) {
      super(ioe);
    }
  }

  public static final class Factory implements Job.Factory<MultiDeviceContactUpdateJob> {
    @Override
    public @NonNull MultiDeviceContactUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      String  serialized = data.getString(KEY_ADDRESS);
      Address address    = serialized != null ? Address.fromSerialized(serialized) : null;

      return new MultiDeviceContactUpdateJob(parameters, address, data.getBoolean(KEY_FORCE_SYNC));
    }
  }
}
