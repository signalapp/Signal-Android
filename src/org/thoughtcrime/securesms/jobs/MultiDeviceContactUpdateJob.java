package org.thoughtcrime.securesms.jobs;

import android.Manifest;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.jobmanager.requirements.NetworkRequirement;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
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
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class MultiDeviceContactUpdateJob extends MasterSecretJob implements InjectableType {

  private static final long serialVersionUID = 2L;

  private static final String TAG = MultiDeviceContactUpdateJob.class.getSimpleName();

  private static final long FULL_SYNC_TIME = TimeUnit.HOURS.toMillis(6);

  @Inject transient SignalServiceMessageSender messageSender;

  private final @Nullable String address;

  private boolean forceSync;

  public MultiDeviceContactUpdateJob(@NonNull Context context) {
    this(context, false);
  }

  public MultiDeviceContactUpdateJob(@NonNull Context context, boolean forceSync) {
    this(context, null, forceSync);
  }

  public MultiDeviceContactUpdateJob(@NonNull Context context, @Nullable Address address) {
    this(context, address, true);
  }

  public MultiDeviceContactUpdateJob(@NonNull Context context, @Nullable Address address, boolean forceSync) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withRequirement(new MasterSecretRequirement(context))
                                .withGroupId(MultiDeviceContactUpdateJob.class.getSimpleName())
                                .withPersistence()
                                .create());

    this.forceSync = forceSync;

    if (address != null) this.address = address.serialize();
    else                 this.address = null;
  }

  @Override
  public void onRun(MasterSecret masterSecret)
      throws IOException, UntrustedIdentityException, NetworkException
  {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.w(TAG, "Not multi device, aborting...");
      return;
    }

    if (address == null) generateFullContactUpdate();
    else                 generateSingleContactUpdate(Address.fromSerialized(address));
  }

  private void generateSingleContactUpdate(@NonNull Address address)
      throws IOException, UntrustedIdentityException, NetworkException
  {
    File contactDataFile = createTempFile("multidevice-contact-update");

    try {
      DeviceContactsOutputStream                out             = new DeviceContactsOutputStream(new FileOutputStream(contactDataFile));
      Recipient                                 recipient       = Recipient.from(context, address, false);
      Optional<IdentityDatabase.IdentityRecord> identityRecord  = DatabaseFactory.getIdentityDatabase(context).getIdentity(address);
      Optional<VerifiedMessage>                 verifiedMessage = getVerifiedMessage(recipient, identityRecord);

      out.write(new DeviceContact(address.toPhoneString(),
                                  Optional.fromNullable(recipient.getName()),
                                  getAvatar(recipient.getContactUri()),
                                  Optional.fromNullable(recipient.getColor().serialize()),
                                  verifiedMessage,
                                  Optional.fromNullable(recipient.getProfileKey()),
                                  recipient.isBlocked(),
                                  recipient.getExpireMessages() > 0 ?
                                      Optional.of(recipient.getExpireMessages()) :
                                      Optional.absent()));

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
    if (!Permissions.hasAny(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
      Log.w(TAG, "No contact permissions, skipping multi-device contact update...");
      return;
    }

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
      DeviceContactsOutputStream out      = new DeviceContactsOutputStream(new FileOutputStream(contactDataFile));
      Collection<ContactData>    contacts = ContactAccessor.getInstance().getContactsWithPush(context);

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
        out.write(new DeviceContact(TextSecurePreferences.getLocalNumber(context),
                                    Optional.absent(), Optional.absent(),
                                    Optional.absent(), Optional.absent(),
                                    Optional.of(ProfileKeyUtil.getProfileKey(context)),
                                    false, Optional.absent()));
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

      try {
        messageSender.sendMessage(SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, complete)));
      } catch (IOException ioe) {
        throw new NetworkException(ioe);
      }
    }
  }

  private Optional<SignalServiceAttachmentStream> getAvatar(@Nullable Uri uri) throws IOException {
    if (uri == null) {
      return Optional.absent();
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      try {
        Uri                 displayPhotoUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO);
        AssetFileDescriptor fd              = context.getContentResolver().openAssetFileDescriptor(displayPhotoUri, "r");

        if (fd == null) {
          return Optional.absent();
        }

        return Optional.of(SignalServiceAttachment.newStreamBuilder()
                                                  .withStream(fd.createInputStream())
                                                  .withContentType("image/*")
                                                  .withLength(fd.getLength())
                                                  .build());
      } catch (IOException e) {
        Log.w(TAG, e);
      }
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

}
