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

import network.loki.messenger.R;

public final class ContactUtil {

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
}
