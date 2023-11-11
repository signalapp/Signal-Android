/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

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
