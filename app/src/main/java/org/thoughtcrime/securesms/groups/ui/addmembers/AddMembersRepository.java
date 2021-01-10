package org.thoughtcrime.securesms.groups.ui.addmembers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.RecipientId;

final class AddMembersRepository {

  private final Context context;
  private final GroupId groupId;

  AddMembersRepository(@NonNull GroupId groupId) {
    this.groupId = groupId;
    this.context = ApplicationDependencies.getApplication();
  }

  @WorkerThread
  RecipientId getOrCreateRecipientId(@NonNull SelectedContact selectedContact) {
    return selectedContact.getOrCreateRecipientId(context);
  }

  @WorkerThread
  String getGroupTitle() {
    return DatabaseFactory.getGroupDatabase(context).requireGroup(groupId).getTitle();
  }
}
