package org.thoughtcrime.securesms.mediasend;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.List;

/**
 * Represents the list of results to display in the {@link CameraContactSelectionFragment}.
 */
public class CameraContacts {

  private final List<Recipient> recents;
  private final List<Recipient> contacts;
  private final List<Recipient> groups;

  public CameraContacts(@NonNull List<Recipient> recents, @NonNull List<Recipient> contacts, @NonNull List<Recipient> groups) {
    this.recents  = recents;
    this.contacts = contacts;
    this.groups   = groups;
  }

  public @NonNull List<Recipient> getRecents() {
    return recents;
  }

  public @NonNull List<Recipient> getContacts() {
    return contacts;
  }

  public @NonNull List<Recipient> getGroups() {
    return groups;
  }

  public boolean isEmpty() {
    return recents.isEmpty() && contacts.isEmpty() && groups.isEmpty();
  }
}
