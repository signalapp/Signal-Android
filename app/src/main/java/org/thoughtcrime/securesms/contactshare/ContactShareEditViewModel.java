package org.thoughtcrime.securesms.contactshare;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.net.Uri;
import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.contactshare.Contact.Name;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

import java.util.ArrayList;
import java.util.List;

class ContactShareEditViewModel extends ViewModel {

  private final MutableLiveData<List<Contact>> contacts;
  private final SingleLiveEvent<Event>         events;
  private final ContactRepository              repo;

  ContactShareEditViewModel(@NonNull List<Uri>         contactUris,
                            @NonNull ContactRepository contactRepository)
  {
    contacts = new MutableLiveData<>();
    events              = new SingleLiveEvent<>();
    repo                = contactRepository;

    repo.getContacts(contactUris, retrieved -> {
      if (retrieved.isEmpty()) {
        events.postValue(Event.BAD_CONTACT);
      } else {
        contacts.postValue(retrieved);
      }
    });
  }

  @NonNull LiveData<List<Contact>> getContacts() {
    return contacts;
  }

  @NonNull List<Contact> getFinalizedContacts() {
    List<Contact> currentContacts = getCurrentContacts();
    List<Contact> trimmedContacts = new ArrayList<>(currentContacts.size());

    for (Contact contact : currentContacts) {
      Contact trimmed = new Contact(contact.getName(),
                                    contact.getOrganization(),
                                    trimSelectables(contact.getPhoneNumbers()),
                                    trimSelectables(contact.getEmails()),
                                    trimSelectables(contact.getPostalAddresses()),
                                    contact.getAvatar() != null && contact.getAvatar().isSelected() ? contact.getAvatar() : null);
      trimmedContacts.add(trimmed);
    }

    return trimmedContacts;
  }

  @NonNull LiveData<Event> getEvents() {
    return events;
  }

  void updateContactName(int contactPosition, @NonNull Name name) {
    if (name.isEmpty()) {
      events.postValue(Event.BAD_CONTACT);
      return;
    }

    List<Contact> currentContacts = getCurrentContacts();
    Contact       original        = currentContacts.remove(contactPosition);

    currentContacts.add(new Contact(name,
                                    original.getOrganization(),
                                    original.getPhoneNumbers(),
                                    original.getEmails(),
                                    original.getPostalAddresses(),
                                    original.getAvatar()));

    contacts.postValue(currentContacts);
  }

  private <E extends Selectable> List<E> trimSelectables(List<E> selectables) {
    return Stream.of(selectables).filter(Selectable::isSelected).toList();
  }

  @NonNull
  private List<Contact> getCurrentContacts() {
    List<Contact> currentContacts = contacts.getValue();
    return currentContacts != null ? currentContacts : new ArrayList<>();
  }

  enum Event {
    BAD_CONTACT
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final List<Uri>         contactUris;
    private final ContactRepository contactRepository;

    Factory(@NonNull List<Uri> contactUris, @NonNull ContactRepository contactRepository) {
      this.contactUris       = contactUris;
      this.contactRepository = contactRepository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new ContactShareEditViewModel(contactUris, contactRepository));
    }
  }
}
