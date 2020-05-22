package org.thoughtcrime.securesms.contacts;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Specialised set for {@link SelectedContact} that will not allow more than one entry that
 * {@link SelectedContact#matches(SelectedContact)} any other.
 */
public final class SelectedContactSet {

  private final List<SelectedContact> contacts = new LinkedList<>();

  public boolean add(@NonNull SelectedContact contact) {
    if (contains(contact)) {
      return false;
    }

    contacts.add(contact);
    return true;
  }

  public boolean contains(@NonNull SelectedContact otherContact) {
    for (SelectedContact contact : contacts) {
      if (otherContact.matches(contact)) {
        return true;
      }
    }
    return false;
  }

  public List<SelectedContact> getContacts() {
    return new ArrayList<>(contacts);
  }

  public int size() {
    return contacts.size();
  }

  public void clear() {
    contacts.clear();
  }

  public int remove(@NonNull SelectedContact otherContact) {
    int                       removeCount = 0;
    Iterator<SelectedContact> iterator    = contacts.iterator();

    while (iterator.hasNext()) {
      SelectedContact next = iterator.next();
      if (next.matches(otherContact)) {
        iterator.remove();
        removeCount++;
      }
    }

    return removeCount;
  }
}
