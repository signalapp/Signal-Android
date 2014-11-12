package org.whispersystems.textsecure.push;

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
