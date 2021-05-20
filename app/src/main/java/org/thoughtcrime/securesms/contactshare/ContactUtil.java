package org.thoughtcrime.securesms.contactshare;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;

import org.session.libsession.utilities.Contact;

import org.thoughtcrime.securesms.components.emoji.EmojiStrings;
import org.thoughtcrime.securesms.util.SpanUtil;

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
