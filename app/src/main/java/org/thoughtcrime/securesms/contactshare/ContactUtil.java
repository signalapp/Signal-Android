package org.thoughtcrime.securesms.contactshare;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.signal.core.util.logging.Log;
import org.signal.glide.decryptableuri.DecryptableUri;
import org.thoughtcrime.securesms.R;
import org.signal.emoji.EmojiStrings;
import org.thoughtcrime.securesms.contactshare.Contact.Email;
import org.thoughtcrime.securesms.contactshare.Contact.Phone;
import org.thoughtcrime.securesms.contactshare.Contact.PostalAddress;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.signal.core.util.bitmaps.BitmapDecodingException;
import org.thoughtcrime.securesms.util.ImageCompressionUtil;
import org.thoughtcrime.securesms.util.SignalE164Util;
import org.thoughtcrime.securesms.util.SpanUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ContactUtil {

  private static final String TAG = Log.tag(ContactUtil.class);

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

  /**
   * The name to render for a shared contact, everywhere.
   */
  public static @NonNull String getDisplayName(@Nullable Contact contact) {
    if (contact == null) {
      return "";
    }

    String structuredName = buildStructuredName(contact.getName());

    if (!TextUtils.isEmpty(structuredName)) {
      return structuredName;
    }

    if (!TextUtils.isEmpty(contact.getName().getNickname())) {
      return contact.getName().getNickname();
    }

    if (!TextUtils.isEmpty(contact.getOrganization())) {
      return contact.getOrganization();
    }

    return "";
  }

  /** ProfileName puts the family name first for CJKV, which a bare join would lose. */
  private static @NonNull String buildStructuredName(@NonNull Contact.Name name) {
    boolean hasExtraParts = !TextUtils.isEmpty(name.getPrefix()) ||
                            !TextUtils.isEmpty(name.getMiddleName()) ||
                            !TextUtils.isEmpty(name.getSuffix());

    if (!hasExtraParts) {
      return ProfileName.fromParts(name.getGivenName(), name.getFamilyName()).toString();
    }

    return Stream.of(name.getPrefix(),
                     name.getGivenName(),
                     name.getMiddleName(),
                     name.getFamilyName(),
                     name.getSuffix())
                 .filter(part -> !TextUtils.isEmpty(part))
                 .collect(Collectors.joining(" "));
  }

  public static @NonNull String getPrettyPhoneNumber(@NonNull Phone phoneNumber, @NonNull Locale fallbackLocale) {
    return getPrettyPhoneNumber(phoneNumber.getNumber(), fallbackLocale);
  }

  private static @NonNull String getPrettyPhoneNumber(@NonNull String phoneNumber, @NonNull Locale fallbackLocale) {
    PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    try {
      PhoneNumber parsed = util.parse(phoneNumber, fallbackLocale.getCountry());
      return util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
    } catch (NumberParseException e) {
      return phoneNumber;
    }
  }

  public static @Nullable String getNormalizedPhoneNumber(@Nullable String number) {
    return SignalE164Util.formatAsE164(number != null ? number : "");
  }

  @MainThread
  public static void selectRecipientThroughDialog(@NonNull Context context, @NonNull List<Recipient> choices, @NonNull Locale locale, @NonNull RecipientSelectedCallback callback) {
    if (choices.size() > 1) {
      CharSequence[] values = new CharSequence[choices.size()];

      for (int i = 0; i < values.length; i++) {
        values[i] = getPrettyPhoneNumber(choices.get(i).requireE164(), locale);
      }

      new MaterialAlertDialogBuilder(context)
                     .setItems(values, ((dialog, which) -> callback.onSelected(choices.get(which))))
                     .create()
                     .show();
    } else {
      callback.onSelected(choices.get(0));
    }
  }

  /**
   * Recipients we already know about for the card's numbers. A lookup rather than an insert, so
   * rendering a received card does not create rows for people the user has never contacted.
   */
  public static List<RecipientId> getExistingRecipients(@NonNull Contact contact) {
    return contact
        .getPhoneNumbers()
        .stream()
        .map(phone -> SignalE164Util.formatAsE164(phone.getNumber()))
        .filter(Objects::nonNull)
        .map(e164 -> SignalDatabase.recipients().getByE164(e164).orElse(null))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Picker over the card's own numbers, for when there is no recipient to pick between.
   */
  public static void selectNumberThroughDialog(@NonNull Context context, @NonNull List<String> numbers, @NonNull Locale locale, @NonNull NumberSelectedCallback callback) {
    if (numbers.size() > 1) {
      CharSequence[] values = new CharSequence[numbers.size()];

      for (int i = 0; i < values.length; i++) {
        values[i] = getPrettyPhoneNumber(numbers.get(i), locale);
      }

      new MaterialAlertDialogBuilder(context).setItems(values, ((dialog, which) -> callback.onSelected(numbers.get(which))))
                                             .create()
                                             .show();
    } else {
      callback.onSelected(numbers.get(0));
    }
  }

  @WorkerThread
  public static @NonNull Intent buildAddToContactsIntent(@NonNull Context context, @NonNull Contact contact) {
    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);

    String displayName = getDisplayName(contact);

    if (!TextUtils.isEmpty(displayName)) {
      intent.putExtra(ContactsContract.Intents.Insert.NAME, displayName);
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

    if (contact.getAvatarAttachment() != null && contact.getAvatarAttachment().getUri() != null) {
      try {
        ImageCompressionUtil.Result result = ImageCompressionUtil.compressWithinConstraints(
            context,
            "image/jpeg",
            new DecryptableUri(contact.getAvatarAttachment().getUri()),
            256,
            100_000,
            80
        );

        if (result != null) {
          ContentValues values = new ContentValues();
          values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
          values.put(ContactsContract.CommonDataKinds.Photo.PHOTO, result.getData());

          ArrayList<ContentValues> valuesArray = new ArrayList<>(1);
          valuesArray.add(values);

          intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, valuesArray);
        } else {
          Log.w(TAG, "Failed to compress avatar to fit within size constraints.");
        }
      } catch (BitmapDecodingException e) {
        Log.w(TAG, "Failed to decode avatar for contact.", e);
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

  public interface NumberSelectedCallback {
    void onSelected(@NonNull String number);
  }

  public interface RecipientSelectedCallback {
    void onSelected(@NonNull Recipient recipient);
  }
}
