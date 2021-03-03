package org.thoughtcrime.securesms.contacts.sync;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.profiles.ProfileName;

import java.util.LinkedList;
import java.util.List;

final class ContactHolder {

  private final String                  lookupKey;
  private final List<PhoneNumberRecord> phoneNumberRecords = new LinkedList<>();

  private StructuredNameRecord structuredNameRecord;

  ContactHolder(@NonNull String lookupKey) {
    this.lookupKey = lookupKey;
  }

  @NonNull String getLookupKey() {
    return lookupKey;
  }

  public void addPhoneNumberRecord(@NonNull PhoneNumberRecord phoneNumberRecord) {
    phoneNumberRecords.add(phoneNumberRecord);
  }

  public void setStructuredNameRecord(@NonNull StructuredNameRecord structuredNameRecord) {
    this.structuredNameRecord = structuredNameRecord;
  }

  void commit(@NonNull RecipientDatabase.BulkOperationsHandle handle) {
    for (PhoneNumberRecord phoneNumberRecord : phoneNumberRecords) {
      handle.setSystemContactInfo(phoneNumberRecord.getRecipientId(),
                                  getProfileName(phoneNumberRecord.getDisplayName()),
                                  phoneNumberRecord.getContactPhotoUri(),
                                  phoneNumberRecord.getContactLabel(),
                                  phoneNumberRecord.getPhoneType(),
                                  phoneNumberRecord.getContactUri().toString());
    }
  }

  private @NonNull ProfileName getProfileName(@NonNull String displayName) {
    if (structuredNameRecord.hasGivenName()) {
      return structuredNameRecord.asProfileName();
    } else {
      return ProfileName.asGiven(displayName);
    }
  }

}
