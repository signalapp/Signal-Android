package org.thoughtcrime.securesms.groups.ui.addmembers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

class AddMembersRepository {

  private final Context context;

  AddMembersRepository() {
    this.context = ApplicationDependencies.getApplication();
  }

  void getOrCreateRecipientId(@NonNull SelectedContact selectedContact, @NonNull Consumer<RecipientId> consumer) {
    SignalExecutors.BOUNDED.execute(() -> consumer.accept(selectedContact.getOrCreateRecipientId(context)));
  }
}
