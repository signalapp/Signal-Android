package org.thoughtcrime.securesms.contactshare;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;

import com.annimon.stream.Stream;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiStrings;
import org.thoughtcrime.securesms.contactshare.Contact.Email;
import org.thoughtcrime.securesms.contactshare.Contact.Phone;
import org.thoughtcrime.securesms.contactshare.Contact.PostalAddress;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ContactUtil {

  private static final String TAG = ContactUtil.class.getSimpleName();

  public static long getContactIdFromUri(@NonNull Uri uri) {
    try {
      return Long.parseLong(uri.getLastPathSegment());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  public static @NonNull CharSequence getStringSummary(@NonNull Context context, @NonNull Contact contact) {
    String  contactName = ContactUtil.getDisplayName(contact);

    if (!TextUtils.isEmpty(contactName)) {
      return context.getString(R.string.MessageNotifier_contact_message, EmojiStrings.BUST_IN_SILHOUETTE, contactName);
    }

    return SpanUtil.italic(context.getString(R.string.MessageNotifier_unknown_contact_message));
  }

  public static @NonNull String getDisplayName(@Nullable Contact contact) {
    if (contact == null) {
      return "";
    }

    if (!TextUtils.isEmpty(contact.getName().getDisplayName())) {
      return contact.getName().getDisplayName();
    }

    if (!TextUtils.isEmpty(contact.getOrganization())) {
      return contact.getOrganization();
    }

    return "";
  }

  public static @NonNull String getDisplayNumber(@NonNull Contact contact, @NonNull Locale locale) {
    Phone displayNumber = getPrimaryNumber(contact);

    if (displayNumber != null) {
      return ContactUtil.getPrettyPhoneNumber(displayNumber, locale);
    } else if (contact.getEmails().size() > 0) {
      return contact.getEmails().get(0).getEmail();
    } else {
      return "";
    }
  }

  private static @Nullable Phone getPrimaryNumber(@NonNull Contact contact) {
    if (contact.getPhoneNumbers().size() == 0) {
      return null;
    }

    List<Phone> mobileNumbers = Stream.of(contact.getPhoneNumbers()).filter(number -> number.getType() == Phone.Type.MOBILE).toList();
    if (mobileNumbers.size() > 0) {
      return mobileNumbers.get(0);
    }

    return contact.getPhoneNumbers().get(0);
  }

  public static @NonNull String getPrettyPhoneNumber(@NonNull Phone phoneNumber, @NonNull Locale fallbackLocale) {
    return getPrettyPhoneNumber(phoneNumber.getNumber(), fallbackLocale);
  }

  private static @NonNull String getPrettyPhoneNumber(@NonNull String phoneNumber, @NonNull Locale fallbackLocale) {
    PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    try {
      PhoneNumber parsed = util.parse(phoneNumber, fallbackLocale.getISO3Country());
      return util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
    } catch (NumberParseException e) {
      return phoneNumber;
    }
  }

  public static @NonNull String getNormalizedPhoneNumber(@NonNull Context context, @NonNull String number) {
    Address address = Address.fromExternal(context, number);
    return address.serialize();
  }

  @MainThread
  public static void selectRecipientThroughDialog(@NonNull Context context, @NonNull List<Recipient> choices, @NonNull Locale locale, @NonNull RecipientSelectedCallback callback) {
    if (choices.size() > 1) {
      CharSequence[] values = new CharSequence[choices.size()];

      for (int i = 0; i < values.length; i++) {
        values[i] = getPrettyPhoneNumber(choices.get(i).getAddress().toPhoneString(), locale);
      }

      new AlertDialog.Builder(context)
                     .setItems(values, ((dialog, which) -> callback.onSelected(choices.get(which))))
                     .create()
                     .show();
    } else {
      callback.onSelected(choices.get(0));
    }
  }

  public static List<Recipient> getRecipients(@NonNull Context context, @NonNull Contact contact) {
    return Stream.of(contact.getPhoneNumbers()).map(phone -> Recipient.from(context, Address.fromExternal(context, phone.getNumber()), true)).toList();
  }

  @WorkerThread
  public static @NonNull Intent buildAddToContactsIntent(@NonNull Context context, @NonNull Contact contact) {
    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);

    if (!TextUtils.isEmpty(contact.getName().getDisplayName())) {
      intent.putExtra(ContactsContract.Intents.Insert.NAME, contact.getName().getDisplayName());
    }

    if (!TextUtils.isEmpty(contact.getOrganization())) {
      intent.putExtra(ContactsContract.Intents.Insert.COMPANY, contact.getOrganization());
    }

    if (contact.getPhoneNumbers().size() > 0) {
      intent.putExtra(ContactsContract.Intents.Insert.PHONE, contact.getPhoneNumbers().get(0).getNumber());
      intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, getSystemType(contact.getPhoneNumbers().get(0).getType()));
    }

    if (contact.getPhoneNumbers().size() > 1) {
      intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, contact.getPhoneNumbers().get(1).getNumber());
      intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE, getSystemType(contact.getPhoneNumbers().get(1).getType()));
    }

    if (contact.getPhoneNumbers().size() > 2) {
      intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_PHONE, contact.getPhoneNumbers().get(2).getNumber());
      intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_PHONE_TYPE, getSystemType(contact.getPhoneNumbers().get(2).getType()));
    }

    if (contact.getEmails().size() > 0) {
      intent.putExtra(ContactsContract.Intents.Insert.EMAIL, contact.getEmails().get(0).getEmail());
      intent.putExtra(ContactsContract.Intents.Insert.EMAIL_TYPE, getSystemType(contact.getEmails().get(0).getType()));
    }

    if (contact.getEmails().size() > 1) {
      intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL, contact.getEmails().get(1).getEmail());
      intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL_TYPE, getSystemType(contact.getEmails().get(1).getType()));
    }

    if (contact.getEmails().size() > 2) {
      intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_EMAIL, contact.getEmails().get(2).getEmail());
      intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_EMAIL_TYPE, getSystemType(contact.getEmails().get(2).getType()));
    }

    if (contact.getPostalAddresses().size() > 0) {
      intent.putExtra(ContactsContract.Intents.Insert.POSTAL, contact.getPostalAddresses().get(0).toString());
      intent.putExtra(ContactsContract.Intents.Insert.POSTAL_TYPE, getSystemType(contact.getPostalAddresses().get(0).getType()));
    }

    if (contact.getAvatarAttachment() != null && contact.getAvatarAttachment().getDataUri() != null) {
      try {
        ContentValues values = new ContentValues();
        values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
        values.put(ContactsContract.CommonDataKinds.Photo.PHOTO, Util.readFully(PartAuthority.getAttachmentStream(context, contact.getAvatarAttachment().getDataUri())));

        ArrayList<ContentValues> valuesArray = new ArrayList<>(1);
        valuesArray.add(values);

        intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, valuesArray);
      } catch (IOException e) {
        Log.w(TAG, "Failed to read avatar into a byte array.", e);
      }
    }
    return intent;
  }

  private static int getSystemType(Phone.Type type) {
    switch (type) {
      case HOME:   return ContactsContract.CommonDataKinds.Phone.TYPE_HOME;
      case MOBILE: return ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
      case WORK:   return ContactsContract.CommonDataKinds.Phone.TYPE_WORK;
      default:     return ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM;
    }
  }

  private static int getSystemType(Email.Type type) {
    switch (type) {
      case HOME:   return ContactsContract.CommonDataKinds.Email.TYPE_HOME;
      case MOBILE: return ContactsContract.CommonDataKinds.Email.TYPE_MOBILE;
      case WORK:   return ContactsContract.CommonDataKinds.Email.TYPE_WORK;
      default:     return ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM;
    }
  }

  private static int getSystemType(PostalAddress.Type type) {
    switch (type) {
      case HOME: return ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME;
      case WORK: return ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK;
      default:   return ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM;
    }
  }

  public interface RecipientSelectedCallback {
    void onSelected(@NonNull Recipient recipient);
  }
}
