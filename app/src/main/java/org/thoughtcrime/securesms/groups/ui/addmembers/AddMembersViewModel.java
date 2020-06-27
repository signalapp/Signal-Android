package org.thoughtcrime.securesms.groups.ui.addmembers;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Preconditions;

import java.util.List;
import java.util.Objects;

public final class AddMembersViewModel extends ViewModel {

  private final AddMembersRepository                                repository;
  private final LiveData<AddMemberDialogMessageState>               addMemberDialogState;
  private final MutableLiveData<AddMemberDialogMessageStatePartial> partialState;

  private AddMembersViewModel(@NonNull GroupId groupId) {
    repository           = new AddMembersRepository();
    partialState         = new MutableLiveData<>();
    addMemberDialogState = LiveDataUtil.combineLatest(Transformations.map(new LiveGroup(groupId).getTitle(), AddMembersViewModel::titleOrDefault),
                                                      Transformations.switchMap(partialState, AddMembersViewModel::getStateWithoutGroupTitle),
                                                      AddMembersViewModel::getStateWithGroupTitle);
  }

  LiveData<AddMemberDialogMessageState> getAddMemberDialogState() {
    return addMemberDialogState;
  }

  void setDialogStateForSelectedContacts(@NonNull List<SelectedContact> selectedContacts) {
    if (selectedContacts.size() == 1) {
      setDialogStateForSingleRecipient(selectedContacts.get(0));
    } else {
      setDialogStateForMultipleRecipients(selectedContacts.size());
    }
  }

  private void setDialogStateForSingleRecipient(@NonNull SelectedContact selectedContact) {
    //noinspection CodeBlock2Expr
    repository.getOrCreateRecipientId(selectedContact, recipientId -> {
      partialState.postValue(new AddMemberDialogMessageStatePartial(recipientId));
    });
  }

  private void setDialogStateForMultipleRecipients(int recipientCount) {
    partialState.setValue(new AddMemberDialogMessageStatePartial(recipientCount));
  }

  private static LiveData<AddMemberDialogMessageState> getStateWithoutGroupTitle(@NonNull AddMemberDialogMessageStatePartial partialState) {
    if (partialState.recipientId != null) {
      return Transformations.map(Recipient.live(partialState.recipientId).getLiveData(), r -> new AddMemberDialogMessageState(r, ""));
    } else {
      return new DefaultValueLiveData<>(new AddMemberDialogMessageState(partialState.memberCount, ""));
    }
  }

  private static AddMemberDialogMessageState getStateWithGroupTitle(@NonNull String title, @NonNull AddMemberDialogMessageState stateWithoutTitle) {
    return new AddMemberDialogMessageState(stateWithoutTitle.recipient, stateWithoutTitle.selectionCount, title);
  }

  private static @NonNull String titleOrDefault(@Nullable String title) {
    return TextUtils.isEmpty(title) ? ApplicationDependencies.getApplication().getString(R.string.Recipient_unknown)
                                    : Objects.requireNonNull(title);
  }

  private static final class AddMemberDialogMessageStatePartial {
    private final RecipientId recipientId;
    private final int         memberCount;

    private AddMemberDialogMessageStatePartial(@NonNull RecipientId recipientId) {
      this.recipientId = recipientId;
      this.memberCount = 1;
    }

    private AddMemberDialogMessageStatePartial(int memberCount) {
      Preconditions.checkArgument(memberCount > 1);
      this.memberCount = memberCount;
      this.recipientId = null;
    }
  }

  public static final class AddMemberDialogMessageState {
    private final Recipient recipient;
    private final String    groupTitle;
    private final int       selectionCount;

    private AddMemberDialogMessageState(@NonNull Recipient recipient, @NonNull String groupTitle) {
      this(recipient, 1, groupTitle);
    }

    private AddMemberDialogMessageState(int selectionCount, @NonNull String groupTitle) {
      this(null, selectionCount, groupTitle);
    }

    private AddMemberDialogMessageState(@Nullable Recipient recipient, int selectionCount, @NonNull String groupTitle) {
      this.recipient      = recipient;
      this.groupTitle     = groupTitle;
      this.selectionCount = selectionCount;
    }

    public Recipient getRecipient() {
      return recipient;
    }

    public int getSelectionCount() {
      return selectionCount;
    }

    public @NonNull String getGroupTitle() {
      return groupTitle;
    }
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final GroupId groupId;

    public Factory(@NonNull GroupId groupId) {
      this.groupId = groupId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new AddMembersViewModel(groupId)));
    }
  }
}
