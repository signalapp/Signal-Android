package org.whispersystems.textsecure.internal.push;

import org.whispersystems.textsecure.api.push.ContactTokenDetails;

import java.util.List;

public class ContactTokenDetailsList {

  private List<ContactTokenDetails> contacts;

  public ContactTokenDetailsList() {}

  public List<ContactTokenDetails> getContacts() {
    return contacts;
  }
}
