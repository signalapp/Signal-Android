package org.thoughtcrime.securesms.contactshare;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contactshare.Contact.Email;
import org.thoughtcrime.securesms.contactshare.Contact.Name;
import org.thoughtcrime.securesms.contactshare.Contact.Phone;
import org.thoughtcrime.securesms.contactshare.Contact.PostalAddress;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
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

      ezvcard.property.StructuredName  vName            = vcard.getStructuredName();
      List<ezvcard.property.Telephone> vPhones          = vcard.getTelephoneNumbers();
      List<ezvcard.property.Email>     vEmails          = vcard.getEmails();
      List<ezvcard.property.Address>   vPostalAddresses = vcard.getAddresses();

      String organization = vcard.getOrganization() != null && !vcard.getOrganization().getValues().isEmpty() ? vcard.getOrganization().getValues().get(0) : null;
      String displayName  = vcard.getFormattedName() != null ? vcard.getFormattedName().getValue() : null;

      if (displayName == null && vName != null) {
        displayName = vName.getGiven();
      }

      if (displayName == null && vcard.getOrganization() != null) {
        displayName = organization;
      }

      if (displayName == null) {
        throw new IOException("No valid name.");
      }

      Name name = new Name(displayName,
                           vName != null ? vName.getGiven() : null,
                           vName != null ? vName.getFamily() : null,
                           vName != null && !vName.getPrefixes().isEmpty() ? vName.getPrefixes().get(0) : null,
                           vName != null && !vName.getSuffixes().isEmpty() ? vName.getSuffixes().get(0) : null,
                           null);


      List<Phone> phoneNumbers = new ArrayList<>(vPhones.size());
      for (ezvcard.property.Telephone vEmail : vPhones) {
        String label = !vEmail.getTypes().isEmpty() ? getCleanedVcardType(vEmail.getTypes().get(0).getValue()) : null;
        phoneNumbers.add(new Phone(vEmail.getText(), phoneTypeFromVcardType(label), label));
      }

      List<Email> emails = new ArrayList<>(vEmails.size());
      for (ezvcard.property.Email vEmail : vEmails) {
        String label = !vEmail.getTypes().isEmpty() ? getCleanedVcardType(vEmail.getTypes().get(0).getValue()) : null;
        emails.add(new Email(vEmail.getValue(), emailTypeFromVcardType(label), label));
      }

      List<PostalAddress> postalAddresses = new ArrayList<>(vPostalAddresses.size());
      for (ezvcard.property.Address vPostalAddress : vPostalAddresses) {
        String label = !vPostalAddress.getTypes().isEmpty() ? getCleanedVcardType(vPostalAddress.getTypes().get(0).getValue()) : null;
        postalAddresses.add(new PostalAddress(postalAddressTypeFromVcardType(label),
                                              label,
                                              vPostalAddress.getStreetAddress(),
                                              vPostalAddress.getPoBox(),
                                              null,
                                              vPostalAddress.getLocality(),
                                              vPostalAddress.getRegion(),
                                              vPostalAddress.getPostalCode(),
                                              vPostalAddress.getCountry()));
      }

      contact = new Contact(name, organization, phoneNumbers, emails, postalAddresses, null);
    } catch (IOException e) {
      Log.w(TAG, "Failed to parse the vcard.", e);
    }

    if (PersistentBlobProvider.AUTHORITY.equals(uri.getAuthority())) {
      PersistentBlobProvider.getInstance(context).delete(context, uri);
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

  private Phone.Type phoneTypeFromVcardType(@Nullable String type) {
    if      ("home".equalsIgnoreCase(type)) return Phone.Type.HOME;
    else if ("cell".equalsIgnoreCase(type)) return Phone.Type.MOBILE;
    else if ("work".equalsIgnoreCase(type)) return Phone.Type.WORK;
    else                                    return Phone.Type.CUSTOM;
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

  private Email.Type emailTypeFromVcardType(@Nullable String type) {
    if      ("home".equalsIgnoreCase(type)) return Email.Type.HOME;
    else if ("cell".equalsIgnoreCase(type)) return Email.Type.MOBILE;
    else if ("work".equalsIgnoreCase(type)) return Email.Type.WORK;
    else                                    return Email.Type.CUSTOM;
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

  private PostalAddress.Type postalAddressTypeFromVcardType(@Nullable String type) {
    if      ("home".equalsIgnoreCase(type)) return PostalAddress.Type.HOME;
    else if ("work".equalsIgnoreCase(type)) return PostalAddress.Type.WORK;
    else                                    return PostalAddress.Type.CUSTOM;
  }

  private String getCleanedVcardType(@Nullable String type) {
    if (TextUtils.isEmpty(type)) return "";

    if (type.startsWith("x-") && type.length() > 2) {
      return type.substring(2);
    }

    return type;
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
