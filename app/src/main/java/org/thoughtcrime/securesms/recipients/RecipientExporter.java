package org.thoughtcrime.securesms.recipients;

import android.content.Intent;
import android.provider.ContactsContract;
import android.text.TextUtils;

import static android.content.Intent.ACTION_INSERT_OR_EDIT;

public final class RecipientExporter {

  public static RecipientExporter export(Recipient recipient) {
    return new RecipientExporter(recipient);
  }

  private final Recipient recipient;

  private RecipientExporter(Recipient recipient) {
    this.recipient = recipient;
  }

  public Intent asAddContactIntent() {
    Intent intent = new Intent(ACTION_INSERT_OR_EDIT);
    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);

    addNameToIntent(intent, recipient.getProfileName().toString());
    addAddressToIntent(intent, recipient);
    return intent;
  }

  private static void addNameToIntent(Intent intent, String profileName) {
    if (!TextUtils.isEmpty(profileName)) {
      intent.putExtra(ContactsContract.Intents.Insert.NAME, profileName);
    }
  }

  private static void addAddressToIntent(Intent intent, Recipient recipient) {
    if (recipient.getE164().isPresent() && recipient.shouldShowE164()) {
      intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.requireE164());
    } else if (recipient.getEmail().isPresent()) {
      intent.putExtra(ContactsContract.Intents.Insert.EMAIL, recipient.requireEmail());
    }
  }
}
