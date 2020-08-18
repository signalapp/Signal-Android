package org.thoughtcrime.securesms.conversation.ui.mentions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import org.thoughtcrime.securesms.megaphone.MegaphoneRepository;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MappingModel;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.List;
import java.util.Objects;

public class MentionsPickerViewModel extends ViewModel {

  private final SingleLiveEvent<Recipient>      selectedRecipient;
  private final LiveData<List<MappingModel<?>>> mentionList;
  private final MutableLiveData<LiveGroup>      group;
  private final MutableLiveData<Query>          liveQuery;
  private final MutableLiveData<Boolean>        isShowing;
  private final MegaphoneRepository             megaphoneRepository;

  MentionsPickerViewModel(@NonNull MentionsPickerRepository mentionsPickerRepository, @NonNull MegaphoneRepository megaphoneRepository) {
    this.megaphoneRepository = megaphoneRepository;

    group             = new MutableLiveData<>();
    liveQuery         = new MutableLiveData<>(Query.NONE);
    selectedRecipient = new SingleLiveEvent<>();
    isShowing         = new MutableLiveData<>(false);

    LiveData<List<FullMember>> fullMembers  = Transformations.distinctUntilChanged(Transformations.switchMap(group, LiveGroup::getFullMembers));
    LiveData<Query>            query        = Transformations.distinctUntilChanged(liveQuery);
    LiveData<MentionQuery>     mentionQuery = LiveDataUtil.combineLatest(query, fullMembers, (q, m) -> new MentionQuery(q.query, m));

    mentionList = LiveDataUtil.mapAsync(mentionQuery, q -> Stream.of(mentionsPickerRepository.search(q)).<MappingModel<?>>map(MentionViewState::new).toList());
  }

  @NonNull LiveData<List<MappingModel<?>>> getMentionList() {
    return mentionList;
  }

  void onSelectionChange(@NonNull Recipient recipient) {
    selectedRecipient.setValue(recipient);
    megaphoneRepository.markFinished(Megaphones.Event.MENTIONS);
  }

  void setIsShowing(boolean isShowing) {
    if (Objects.equals(this.isShowing.getValue(), isShowing)) {
      return;
    }
    this.isShowing.setValue(isShowing);
  }

  public @NonNull LiveData<Recipient> getSelectedRecipient() {
    return selectedRecipient;
  }

  public @NonNull LiveData<Boolean> isShowing() {
    return isShowing;
  }

  public void onQueryChange(@Nullable String query) {
    liveQuery.setValue(query == null ? Query.NONE : new Query(query));
  }

  public void onRecipientChange(@NonNull Recipient recipient) {
    GroupId groupId = recipient.getGroupId().orNull();
    if (groupId != null) {
      LiveGroup liveGroup = new LiveGroup(groupId);
      group.setValue(liveGroup);
    }
  }

  /**
   * Wraps a nullable query string so it can be properly propagated through
   * {@link LiveDataUtil#combineLatest(LiveData, LiveData, LiveDataUtil.Combine)}.
   */
  private static class Query {
    static final Query NONE = new Query(null);

    @Nullable private final String query;

    Query(@Nullable String query) {
      this.query = query;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }

      if (object == null || getClass() != object.getClass()) {
        return false;
      }

      Query other = (Query) object;
      return Objects.equals(query, other.query);
    }

    @Override
    public int hashCode() {
      return Objects.hash(query);
    }
  }

  public static final class Factory implements ViewModelProvider.Factory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new MentionsPickerViewModel(new MentionsPickerRepository(ApplicationDependencies.getApplication()),
                                                         ApplicationDependencies.getMegaphoneRepository()));
    }
  }
}
