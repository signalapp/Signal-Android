package org.thoughtcrime.securesms.groups.ui.creategroup.details;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AddGroupDetailsViewModel extends ViewModel {

  private final LiveData<List<GroupMemberEntry.NewGroupCandidate>> members;
  private final DefaultValueLiveData<Set<RecipientId>>             deleted           = new DefaultValueLiveData<>(new HashSet<>());
  private final MutableLiveData<String>                            name              = new MutableLiveData<>("");
  private final MutableLiveData<byte[]>                            avatar            = new MutableLiveData<>();
  private final SingleLiveEvent<GroupCreateResult>                 groupCreateResult = new SingleLiveEvent<>();
  private final LiveData<Boolean>                                  isMms;
  private final LiveData<Boolean>                                  canSubmitForm;
  private final AddGroupDetailsRepository                          repository;

  private AddGroupDetailsViewModel(@NonNull RecipientId[] recipientIds,
                                   @NonNull AddGroupDetailsRepository repository)
  {
    this.repository = repository;

    MutableLiveData<List<GroupMemberEntry.NewGroupCandidate>> initialMembers = new MutableLiveData<>();

    LiveData<Boolean> isValidName = Transformations.map(name, name -> !TextUtils.isEmpty(name));
    members       = LiveDataUtil.combineLatest(initialMembers, deleted, AddGroupDetailsViewModel::filterDeletedMembers);
    isMms         = Transformations.map(members, this::isAnyForcedSms);
    canSubmitForm = LiveDataUtil.combineLatest(isMms, isValidName, (mms, validName) -> mms || validName);

    repository.resolveMembers(recipientIds, initialMembers::postValue);
  }

  @NonNull LiveData<List<GroupMemberEntry.NewGroupCandidate>> getMembers() {
    return members;
  }

  @NonNull LiveData<Boolean> getCanSubmitForm() {
    return canSubmitForm;
  }

  @NonNull LiveData<GroupCreateResult> getGroupCreateResult() {
    return groupCreateResult;
  }

  @NonNull LiveData<byte[]> getAvatar() {
    return avatar;
  }

  @NonNull LiveData<Boolean> getIsMms() {
    return isMms;
  }

  void setAvatar(@Nullable byte[] avatar) {
    this.avatar.setValue(avatar);
  }

  boolean hasAvatar() {
    return avatar.getValue() != null;
  }

  void setName(@NonNull String name) {
    this.name.setValue(name);
  }

  void delete(@NonNull RecipientId recipientId) {
    Set<RecipientId> deleted  = this.deleted.getValue();

    deleted.add(recipientId);
    this.deleted.setValue(deleted);
  }

  void create() {
    List<GroupMemberEntry.NewGroupCandidate> members     = Objects.requireNonNull(this.members.getValue());
    Set<RecipientId>                         memberIds   = Stream.of(members).map(member -> member.getMember().getId()).collect(Collectors.toSet());
    byte[]                                   avatarBytes = avatar.getValue();
    boolean                                  isGroupMms  = isMms.getValue() == Boolean.TRUE;
    String                                   groupName   = isGroupMms ? "" : name.getValue();

    if (!isGroupMms && TextUtils.isEmpty(groupName)) {
      groupCreateResult.postValue(GroupCreateResult.error(GroupCreateResult.Error.Type.ERROR_INVALID_NAME));
      return;
    }

    if (memberIds.isEmpty()) {
      groupCreateResult.postValue(GroupCreateResult.error(GroupCreateResult.Error.Type.ERROR_INVALID_MEMBER_COUNT));
      return;
    }

    repository.createPushGroup(memberIds,
                               avatarBytes,
                               groupName,
                               isGroupMms,
                               groupCreateResult::postValue);
  }

  private static @NonNull List<GroupMemberEntry.NewGroupCandidate> filterDeletedMembers(@NonNull List<GroupMemberEntry.NewGroupCandidate> members, @NonNull Set<RecipientId> deleted) {
    return Stream.of(members)
                 .filterNot(member -> deleted.contains(member.getMember().getId()))
                 .toList();
  }

  private boolean isAnyForcedSms(@NonNull List<GroupMemberEntry.NewGroupCandidate> members) {
    return Stream.of(members)
                 .anyMatch(member -> !member.getMember().isRegistered());
  }

  static final class Factory implements ViewModelProvider.Factory {

    private final RecipientId[]             recipientIds;
    private final AddGroupDetailsRepository repository;

    Factory(@NonNull RecipientId[] recipientIds, @NonNull AddGroupDetailsRepository repository) {
      this.recipientIds = recipientIds;
      this.repository   = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new AddGroupDetailsViewModel(recipientIds, repository)));
    }
  }
}
