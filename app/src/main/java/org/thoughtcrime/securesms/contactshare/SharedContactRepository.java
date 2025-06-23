package org.thoughtcrime.securesms.contactshare;

import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.contacts.SystemContactsRepository;
import org.signal.contacts.SystemContactsRepository.NameDetails;
import org.signal.contacts.SystemContactsRepository.PhoneDetails;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contactshare.Contact.Email;
import org.thoughtcrime.securesms.contactshare.Contact.Name;
import org.thoughtcrime.securesms.contactshare.Contact.Phone;
import org.thoughtcrime.securesms.contactshare.Contact.PostalAddress;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.SignalE164Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import ezvcard.Ezvcard;
import ezvcard.VCard;

import static org.thoughtcrime.securesms.contactshare.Contact.Avatar;

public class SharedContactRepository {

  private static final String TAG = Log.tag(SharedContactRepository.class);

  private final Context  context;
  private final Executor executor;

  SharedContactRepository(@NonNull Context context, @NonNull Executor executor) {
    this.context  = context.getApplicationContext();
    this.executor = executor;
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
    List<Phone> phoneNumbers = getPhoneNumbers(contactId);
    List<Email> emails       = getEmails(contactId);

    if (phoneNumbers.isEmpty() && emails.isEmpty()) {
      Log.w(TAG, "Couldn't find a phone number or email address associated with the provided contact ID.");
      return null;
    }

    Name       name       = getName(contactId);
    AvatarInfo avatarInfo = getAvatarInfo(contactId, phoneNumbers);
    Avatar     avatar     = avatarInfo != null ? new Avatar(avatarInfo.uri, avatarInfo.isProfile) : null;

    return new Contact(name, null, phoneNumbers, emails, getPostalAddresses(contactId), avatar);
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
    NameDetails nameDetails = SystemContactsRepository.getNameDetails(context, contactId);

    if (nameDetails != null) {
      Name name = new Name(nameDetails.getGivenName(), nameDetails.getFamilyName(), nameDetails.getPrefix(), nameDetails.getSuffix(), nameDetails.getMiddleName(), null);
      if (!name.isEmpty()) {
        return name;
      }
    }

    String org = SystemContactsRepository.getOrganizationName(context, contactId);
    if (!TextUtils.isEmpty(org)) {
      return new Name(org, null, null, null, null, null);
    }

    return null;
  }

  @WorkerThread
  private @NonNull List<Phone> getPhoneNumbers(long contactId) {
    Map<String, Phone> numberMap    = new HashMap<>();
    List<PhoneDetails> phoneDetails = SystemContactsRepository.getPhoneDetails(context, contactId);

    for (PhoneDetails phone : phoneDetails) {
      String number = ContactUtil.getNormalizedPhoneNumber(phone.getNumber());
      if (number == null) {
        continue;
      }

      Phone  existing  = numberMap.get(number);
      Phone  candidate = new Phone(number, VCardUtil.phoneTypeFromContactType(phone.getType()), phone.getLabel());

      if (existing == null || (existing.getType() == Phone.Type.CUSTOM && existing.getLabel() == null)) {
        numberMap.put(number, candidate);
      }
    }

    List<Phone> numbers = new ArrayList<>(numberMap.size());
    numbers.addAll(numberMap.values());
    return numbers;
  }

  @WorkerThread
  private @NonNull List<Email> getEmails(long contactId) {
    return SystemContactsRepository.getEmailDetails(context, contactId)
                                   .stream()
                                   .filter(Objects::nonNull)
                                   .map(email -> new Email(Objects.requireNonNull(email.getAddress()),
                                                           VCardUtil.emailTypeFromContactType(email.getType()),
                                                           email.getLabel()))
                                   .collect(Collectors.toList());
  }

  @WorkerThread
  private @NonNull List<PostalAddress> getPostalAddresses(long contactId) {
    return SystemContactsRepository.getPostalAddressDetails(context, contactId)
                                   .stream()
                                   .map(address -> {
                                     return new PostalAddress(VCardUtil.postalAddressTypeFromContactType(address.getType()),
                                                              address.getLabel(),
                                                              address.getStreet(),
                                                              address.getPoBox(),
                                                              address.getNeighborhood(),
                                                              address.getCity(),
                                                              address.getRegion(),
                                                              address.getPostal(),
                                                              address.getCountry());
                                   })
                                   .collect(Collectors.toList());
  }

  @WorkerThread
  private @Nullable AvatarInfo getAvatarInfo(long contactId, List<Phone> phoneNumbers) {
    AvatarInfo systemAvatar = getSystemAvatarInfo(contactId);

    if (systemAvatar != null) {
      return systemAvatar;
    }

    for (Phone phoneNumber : phoneNumbers) {
      String formattedNumber = SignalE164Util.formatAsE164(phoneNumber.getNumber());
      if (formattedNumber == null) {
        continue;
      }

      AvatarInfo recipientAvatar = getRecipientAvatarInfo(formattedNumber);
      if (recipientAvatar != null) {
        return recipientAvatar;
      }
    }
    return null;
  }

  @WorkerThread
  private @Nullable AvatarInfo getSystemAvatarInfo(long contactId) {
    Uri uri = SystemContactsRepository.getAvatarUri(context, contactId);
    if (uri != null) {
      return new AvatarInfo(uri, false);
    }

    return null;
  }

  @WorkerThread
  private @Nullable AvatarInfo getRecipientAvatarInfo(String address) {
    Recipient recipient = Recipient.external(address);
    if (recipient == null) {
      return null;
    }

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
      this.uri       = uri;
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
