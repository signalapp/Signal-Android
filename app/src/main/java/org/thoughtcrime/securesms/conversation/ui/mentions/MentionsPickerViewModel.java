package org.thoughtcrime.securesms.conversation.ui.mentions;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerRepository.MentionQuery;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry.FullMember;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MappingModel;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.List;

public class MentionsPickerViewModel extends ViewModel {

  private final SingleLiveEvent<Recipient>      selectedRecipient;
  private final LiveData<List<MappingModel<?>>> mentionList;
  private final MutableLiveData<LiveGroup>      group;
  private final MutableLiveData<String>         liveQuery;

  MentionsPickerViewModel(@NonNull MentionsPickerRepository mentionsPickerRepository) {
    group             = new MutableLiveData<>();
    liveQuery         = new MutableLiveData<>();
    selectedRecipient = new SingleLiveEvent<>();

    LiveData<List<FullMember>> fullMembers  = Transformations.distinctUntilChanged(Transformations.switchMap(group, LiveGroup::getFullMembers));
    LiveData<String>           query        = Transformations.distinctUntilChanged(liveQuery);
    LiveData<MentionQuery>     mentionQuery = LiveDataUtil.combineLatest(query, fullMembers, MentionQuery::new);

    mentionList = LiveDataUtil.mapAsync(mentionQuery, q -> Stream.of(mentionsPickerRepository.search(q)).<MappingModel<?>>map(MentionViewState::new).toList());
  }

  @NonNull LiveData<List<MappingModel<?>>> getMentionList() {
    return mentionList;
  }

  void onSelectionChange(@NonNull Recipient recipient) {
    selectedRecipient.setValue(recipient);
  }

  public @NonNull LiveData<Recipient> getSelectedRecipient() {
    return selectedRecipient;
  }

  public void onQueryChange(@NonNull CharSequence query) {
    liveQuery.setValue(query.toString());
  }

  public void onRecipientChange(@NonNull Recipient recipient) {
    GroupId groupId = recipient.getGroupId().orNull();
    if (groupId != null) {
      LiveGroup liveGroup = new LiveGroup(groupId);
      group.setValue(liveGroup);
    }
  }

  public static final class Factory implements ViewModelProvider.Factory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new MentionsPickerViewModel(new MentionsPickerRepository(ApplicationDependencies.getApplication())));
    }
  }
}
