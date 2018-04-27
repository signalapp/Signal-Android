package org.thoughtcrime.securesms.contactshare;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contactshare.Contact.Email;
import org.thoughtcrime.securesms.contactshare.Contact.Name;
import org.thoughtcrime.securesms.contactshare.Contact.Phone;
import org.thoughtcrime.securesms.contactshare.Contact.PostalAddress;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.thoughtcrime.securesms.contactshare.Contact.*;

public class ContactRepository {

  private static final String TAG = ContactRepository.class.getSimpleName();

  private final Context          context;
  private final Executor         executor;
  private final ContactsDatabase contactsDatabase;

  ContactRepository(@NonNull Context          context,
                    @NonNull Executor         executor,
                    @NonNull ContactsDatabase contactsDatabase)
  {
    this.context          = context.getApplicationContext();
    this.executor         = executor;
    this.contactsDatabase = contactsDatabase;
  }

  void getContacts(@NonNull List<Long> contactIds, @NonNull ValueCallback<List<Contact>> callback) {
    executor.execute(() -> {
      List<Contact> contacts = new ArrayList<>(contactIds.size());
      for (long id : contactIds) {
        Contact contact = getContact(id);
        if (contact != null) {
          contacts.add(contact);
        }
      }
      callback.onComplete(contacts);
    });
  }

  @WorkerThread
  private @Nullable Contact getContact(long contactId) {
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
        Phone  candidate = new Phone(number, phoneTypeFromContactType(cursorType), cursorLabel);

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

        emails.add(new Email(cursorEmail, emailTypeFromContactType(cursorType), cursorLabel));
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

        postalAddresses.add(new PostalAddress(postalAddressTypeFromContactType(cursorType),
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
      AvatarInfo recipientAvatar = getRecipientAvatarInfo(Address.fromExternal(context, phoneNumber.getNumber()));
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
  private @Nullable AvatarInfo getRecipientAvatarInfo(@NonNull Address address) {
    Recipient    recipient    = Recipient.from(context, address, false);
    ContactPhoto contactPhoto = recipient.getContactPhoto();

    if (contactPhoto != null) {
      Uri avatarUri = contactPhoto.getUri(context);
      if (avatarUri != null) {
        return new AvatarInfo(avatarUri, contactPhoto.isProfilePhoto());
      }
    }

    return null;
  }

  private Phone.Type phoneTypeFromContactType(int type) {
    switch (type) {
      case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
        return Phone.Type.HOME;
      case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
        return Phone.Type.MOBILE;
      case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
        return Phone.Type.WORK;
    }
    return Phone.Type.CUSTOM;
  }

  private Email.Type emailTypeFromContactType(int type) {
    switch (type) {
      case ContactsContract.CommonDataKinds.Email.TYPE_HOME:
        return Email.Type.HOME;
      case ContactsContract.CommonDataKinds.Email.TYPE_MOBILE:
        return Email.Type.MOBILE;
      case ContactsContract.CommonDataKinds.Email.TYPE_WORK:
        return Email.Type.WORK;
    }
    return Email.Type.CUSTOM;
  }

  private PostalAddress.Type postalAddressTypeFromContactType(int type) {
    switch (type) {
      case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME:
        return PostalAddress.Type.HOME;
      case ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK:
        return PostalAddress.Type.WORK;
    }
    return PostalAddress.Type.CUSTOM;
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
