package org.thoughtcrime.securesms.contactshare;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contactshare.Contact.Email;
import org.thoughtcrime.securesms.contactshare.Contact.Name;
import org.thoughtcrime.securesms.contactshare.Contact.Phone;
import org.thoughtcrime.securesms.contactshare.Contact.PostalAddress;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import ezvcard.Ezvcard;
import ezvcard.VCard;

import static org.thoughtcrime.securesms.contactshare.Contact.Avatar;

public class SharedContactRepository {

  private static final String TAG = SharedContactRepository.class.getSimpleName();

  private final Context          context;
  private final Executor         executor;
  private final ContactsDatabase contactsDatabase;

  SharedContactRepository(@NonNull Context          context,
                          @NonNull Executor         executor,
                          @NonNull ContactsDatabase contactsDatabase)
  {
    this.context          = context.getApplicationContext();
    this.executor         = executor;
    this.contactsDatabase = contactsDatabase;
  }

  void getContacts(@NonNull List<Uri> contactUris, @NonNull ValueCallback<List<Contact>> callback) {
    executor.execute(() -> {
      List<Contact> contacts = new ArrayList<>(contactUris.size());
      for (Uri contactUri : contactUris) {
        Contact contact;

        if (ContactsContract.AUTHORITY.equals(contactUri.getAuthority())) {
          contact = getContactFromSystemContacts(ContactUtil.getContactIdFromUri(contactUri));
        } else {
          contact = getContactFromVcard(contactUri);
        }

        if (contact != null) {
          contacts.add(contact);
        }
      }
      callback.onComplete(contacts);
    });
  }

  @WorkerThread
  private @Nullable Contact getContactFromSystemContacts(long contactId) {
    Name name = getName(contactId);
    if (name == null) {
      Log.w(TAG, "Couldn't find a name associated with the provided contact ID.");
      return null;
    }

    List<Phone> phoneNumbers = getPhoneNumbers(contactId);
    AvatarInfo  avatarInfo   = getAvatarInfo(contactId, phoneNumbers);
    Avatar      avatar       = avatarInfo != null ? new Avatar(avatarInfo.uri, avatarInfo.isProfile) : null;

    return new Contact(name, null, phoneNumbers, getEmails(contactId), getPostalAddresses(contactId), avatar);
  }

  @WorkerThread
  private @Nullable Contact getContactFromVcard(@NonNull Uri uri) {
    Contact contact = null;

    try (InputStream stream = PartAuthority.getAttachmentStream(context, uri)) {
      VCard vcard = Ezvcard.parse(stream).first();
      contact = VCardUtil.getContactFromVcard(vcard);
    } catch (IOException e) {
      Log.w(TAG, "Failed to parse the vcard.", e);
    }

    if (BlobProvider.AUTHORITY.equals(uri.getAuthority())) {
      BlobProvider.getInstance().delete(context, uri);
    }

    return contact;
  }

  @WorkerThread
  private @Nullable Name getName(long contactId) {
    try (Cursor cursor = contactsDatabase.getNameDetails(contactId)) {
      if (cursor != null && cursor.moveToFirst()) {
        String cursorDisplayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME));
        String cursorGivenName   = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME));
        String cursorFamilyName  = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME));
        String cursorPrefix      = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.PREFIX));
        String cursorSuffix      = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.SUFFIX));
        String cursorMiddleName  = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME));

        Name name = new Name(cursorDisplayName, cursorGivenName, cursorFamilyName, cursorPrefix, cursorSuffix, cursorMiddleName);
        if (!name.isEmpty()) {
          return name;
        }
      }
    }

    String org = contactsDatabase.getOrganizationName(contactId);
    if (!TextUtils.isEmpty(org)) {
      return new Name(org, org, null, null, null, null);
    }

    return null;
  }

  @WorkerThread
  private @NonNull List<Phone> getPhoneNumbers(long contactId) {
    Map<String, Phone> numberMap = new HashMap<>();
    try (Cursor cursor = contactsDatabase.getPhoneDetails(contactId)) {
      while (cursor != null && cursor.moveToNext()) {
        String cursorNumber = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
        int    cursorType   = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE));
        String cursorLabel  = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL));

        String number    = ContactUtil.getNormalizedPhoneNumber(context, cursorNumber);
        Phone  existing  = numberMap.get(number);
        Phone  candidate = new Phone(number, VCardUtil.phoneTypeFromContactType(cursorType), cursorLabel);

        if (existing == null || (existing.getType() == Phone.Type.CUSTOM && existing.getLabel() == null)) {
          numberMap.put(number, candidate);
        }
      }
    }

    List<Phone> numbers = new ArrayList<>(numberMap.size());
    numbers.addAll(numberMap.values());
    return numbers;
  }

  @WorkerThread
  private @NonNull List<Email> getEmails(long contactId) {
    List<Email> emails = new LinkedList<>();

    try (Cursor cursor = contactsDatabase.getEmailDetails(contactId)) {
      while (cursor != null && cursor.moveToNext()) {
        String cursorEmail = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS));
        int    cursorType  = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.TYPE));
        String cursorLabel = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.LABEL));

        emails.add(new Email(cursorEmail, VCardUtil.emailTypeFromContactType(cursorType), cursorLabel));
      }
    }

    return emails;
  }

  @WorkerThread
  private @NonNull List<PostalAddress> getPostalAddresses(long contactId) {
    List<PostalAddress> postalAddresses = new LinkedList<>();

    try (Cursor cursor = contactsDatabase.getPostalAddressDetails(contactId)) {
      while (cursor != null && cursor.moveToNext()) {
        int    cursorType         = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.TYPE));
        String cursorLabel        = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.LABEL));
        String cursorStreet       = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.STREET));
        String cursorPoBox        = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.POBOX));
        String cursorNeighborhood = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD));
        String cursorCity         = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.CITY));
        String cursorRegion       = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.REGION));
        String cursorPostal       = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE));
        String cursorCountry      = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY));

        postalAddresses.add(new PostalAddress(VCardUtil.postalAddressTypeFromContactType(cursorType),
                                              cursorLabel,
                                              cursorStreet,
                                              cursorPoBox,
                                              cursorNeighborhood,
                                              cursorCity,
                                              cursorRegion,
                                              cursorPostal,
                                              cursorCountry));
      }
    }

    return postalAddresses;
  }

  @WorkerThread
  private @Nullable AvatarInfo getAvatarInfo(long contactId, List<Phone> phoneNumbers) {
    AvatarInfo systemAvatar = getSystemAvatarInfo(contactId);

    if (systemAvatar != null) {
      return systemAvatar;
    }

    for (Phone phoneNumber : phoneNumbers) {
      AvatarInfo recipientAvatar = getRecipientAvatarInfo(PhoneNumberFormatter.get(context).format(phoneNumber.getNumber()));
      if (recipientAvatar != null) {
        return recipientAvatar;
      }
    }
    return null;
  }

  @WorkerThread
  private @Nullable AvatarInfo getSystemAvatarInfo(long contactId) {
    Uri uri = contactsDatabase.getAvatarUri(contactId);
    if (uri != null) {
      return new AvatarInfo(uri, false);
    }

    return null;
  }

  @WorkerThread
  private @Nullable AvatarInfo getRecipientAvatarInfo(String address) {
    Recipient    recipient    = Recipient.external(context, address);
    ContactPhoto contactPhoto = recipient.getContactPhoto();

    if (contactPhoto != null) {
      Uri avatarUri = contactPhoto.getUri(context);
      if (avatarUri != null) {
        return new AvatarInfo(avatarUri, contactPhoto.isProfilePhoto());
      }
    }

    return null;
  }

  interface ValueCallback<T> {
    void onComplete(@NonNull T value);
  }

  private static class AvatarInfo {

    private final Uri     uri;
    private final boolean isProfile;

    private AvatarInfo(Uri uri, boolean isProfile) {
      this.uri = uri;
      this.isProfile = isProfile;
    }

    public Uri getUri() {
      return uri;
    }

    public boolean isProfile() {
      return isProfile;
    }
  }
}
