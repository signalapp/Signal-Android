package org.whispersystems.textsecure.internal.push;

import java.util.List;

public class ContactTokenList {

  private List<String> contacts;

  public ContactTokenList(List<String> contacts) {
    this.contacts = contacts;
  }

  public ContactTokenList() {}

  public List<String> getContacts() {
    return contacts;
  }
}
