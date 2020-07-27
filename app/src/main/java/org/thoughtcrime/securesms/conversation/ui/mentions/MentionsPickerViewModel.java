package org.thoughtcrime.securesms.conversation.ui.mentions;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry.FullMember;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MappingModel;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.Collections;
import java.util.List;

public class MentionsPickerViewModel extends ViewModel {

  private final SingleLiveEvent<Recipient>      selectedRecipient;
  private final LiveData<List<MappingModel<?>>> mentionList;
  private final MutableLiveData<LiveGroup>      group;
  private final MutableLiveData<CharSequence>   liveQuery;

  MentionsPickerViewModel() {
    group             = new MutableLiveData<>();
    liveQuery         = new MutableLiveData<>();
    selectedRecipient = new SingleLiveEvent<>();

    // TODO [cody] [mentions] simple query support implement for building UI/UX, to be replaced with better search before launch
    LiveData<List<FullMember>> members = Transformations.distinctUntilChanged(Transformations.switchMap(group, LiveGroup::getFullMembers));

    mentionList = LiveDataUtil.combineLatest(Transformations.distinctUntilChanged(liveQuery), members, this::filterMembers);
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
    liveQuery.setValue(query);
  }

  public void onRecipientChange(@NonNull Recipient recipient) {
    GroupId groupId = recipient.getGroupId().orNull();
    if (groupId != null) {
      LiveGroup liveGroup = new LiveGroup(groupId);
      group.setValue(liveGroup);
    }
  }

  private @NonNull List<MappingModel<?>> filterMembers(@NonNull CharSequence query, @NonNull List<FullMember> members) {
    if (TextUtils.isEmpty(query)) {
      return Collections.emptyList();
    }

    return Stream.of(members)
                 .filter(m -> m.getMember().getDisplayName(ApplicationDependencies.getApplication()).toLowerCase().replaceAll("\\s", "").startsWith(query.toString()))
                 .<MappingModel<?>>map(m -> new MentionViewState(m.getMember()))
                 .toList();
  }

  public static final class Factory implements ViewModelProvider.Factory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new MentionsPickerViewModel());
    }
  }
}
