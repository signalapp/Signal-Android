package org.session.libsession.utilities.recipients;

import android.content.Intent;
import android.provider.ContactsContract;
import android.text.TextUtils;

import org.session.libsession.utilities.Address;

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
    addNameToIntent(intent, recipient.getProfileName());
    addAddressToIntent(intent, recipient.getAddress());
    return intent;
  }

  private static void addNameToIntent(Intent intent, String profileName) {
    if (!TextUtils.isEmpty(profileName)) {
      intent.putExtra(ContactsContract.Intents.Insert.NAME, profileName);
    }
  }

  private static void addAddressToIntent(Intent intent, Address address) {
    if (address.isContact()) {
      intent.putExtra(ContactsContract.Intents.Insert.PHONE, address.toString());
    } else {
      throw new RuntimeException("Cannot export Recipient with neither phone nor email");
    }
  }
}
