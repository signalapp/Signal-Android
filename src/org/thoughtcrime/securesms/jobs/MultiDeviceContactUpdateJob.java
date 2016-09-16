package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.TextSecureCommunicationModule.TextSecureMessageSenderFactory;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;

import javax.inject.Inject;

public class MultiDeviceContactUpdateJob extends MasterSecretJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = MultiDeviceContactUpdateJob.class.getSimpleName();

  @Inject transient TextSecureMessageSenderFactory messageSenderFactory;

  private final long recipientId;

  public MultiDeviceContactUpdateJob(Context context) {
    this(context, -1);
  }

  public MultiDeviceContactUpdateJob(Context context, long recipientId) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withRequirement(new MasterSecretRequirement(context))
                                .withGroupId(MultiDeviceContactUpdateJob.class.getSimpleName())
                                .withPersistence()
                                .create());

    this.recipientId = recipientId;
  }

  @Override
  public void onRun(MasterSecret masterSecret)
      throws IOException, UntrustedIdentityException, NetworkException
  {
    if (recipientId <= 0) generateFullContactUpdate();
    else                  generateSingleContactUpdate(recipientId);
  }

  private void generateSingleContactUpdate(long recipientId)
      throws IOException, UntrustedIdentityException, NetworkException
  {
    SignalServiceMessageSender messageSender = messageSenderFactory.create();
    File contactDataFile = createTempFile("multidevice-contact-update");

    try {
      DeviceContactsOutputStream out       = new DeviceContactsOutputStream(new FileOutputStream(contactDataFile));
      Recipient                  recipient = RecipientFactory.getRecipientForId(context, recipientId, false);

      out.write(new DeviceContact(recipient.getNumber(),
                                  Optional.fromNullable(recipient.getName()),
                                  getAvatar(recipient.getContactUri()),
                                  Optional.fromNullable(recipient.getColor().serialize())));

      out.close();
      sendUpdate(messageSender, contactDataFile);

    } finally {
      if (contactDataFile != null) contactDataFile.delete();
    }
  }

  private void generateFullContactUpdate()
      throws IOException, UntrustedIdentityException, NetworkException
  {
    SignalServiceMessageSender messageSender   = messageSenderFactory.create();
    File                       contactDataFile = createTempFile("multidevice-contact-update");

    try {
      DeviceContactsOutputStream out      = new DeviceContactsOutputStream(new FileOutputStream(contactDataFile));
      Collection<ContactData>    contacts = ContactAccessor.getInstance().getContactsWithPush(context);

      for (ContactData contactData : contacts) {
        Uri              contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(contactData.id));
        String           number     = contactData.numbers.get(0).number;
        Optional<String> name       = Optional.fromNullable(contactData.name);
        Optional<String> color      = getColor(number);

        out.write(new DeviceContact(number, name, getAvatar(contactUri), color));
      }

      out.close();
      sendUpdate(messageSender, contactDataFile);

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

  private Optional<String> getColor(String number) {
    if (!TextUtils.isEmpty(number)) {
      Recipients recipients = RecipientFactory.getRecipientsFromString(context, number, false);
      return Optional.of(recipients.getColor().serialize());
    } else {
      return Optional.absent();
    }
  }

  private void sendUpdate(SignalServiceMessageSender messageSender, File contactsFile)
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
        messageSender.sendMessage(SignalServiceSyncMessage.forContacts(attachmentStream));
      } catch (IOException ioe) {
        throw new NetworkException(ioe);
      }
    }
  }

  private Optional<SignalServiceAttachmentStream> getAvatar(Uri uri) throws IOException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      try {
        Uri                 displayPhotoUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO);
        AssetFileDescriptor fd              = context.getContentResolver().openAssetFileDescriptor(displayPhotoUri, "r");

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
