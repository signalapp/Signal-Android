package org.thoughtcrime.securesms.recipients.ui.managerecipient;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.BlockUnblockDialog;
import org.thoughtcrime.securesms.ExpirationDialog;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.MediaDatabase;
import org.thoughtcrime.securesms.database.loaders.MediaLoader;
import org.thoughtcrime.securesms.database.loaders.ThreadMediaLoader;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.groups.ui.addtogroup.AddToGroupsActivity;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.List;

public final class ManageRecipientViewModel extends ViewModel {

  private static final int MAX_COLLAPSED_GROUPS = 5;

  private final Context                                          context;
  private final ManageRecipientRepository                        manageRecipientRepository;
  private final LiveData<String>                                 title;
  private final LiveData<String>                                 subtitle;
  private final LiveData<String>                                 disappearingMessageTimer;
  private final MutableLiveData<IdentityDatabase.IdentityRecord> identity;
  private final LiveData<Recipient>                              recipient;
  private final MutableLiveData<MediaCursor>                     mediaCursor               = new MutableLiveData<>(null);
  private final LiveData<MuteState>                              muteState;
  private final LiveData<Boolean>                                hasCustomNotifications;
  private final LiveData<Boolean>                                canCollapseMemberList;
  private final DefaultValueLiveData<CollapseState>              groupListCollapseState    = new DefaultValueLiveData<>(CollapseState.COLLAPSED);
  private final LiveData<Boolean>                                canBlock;
  private final LiveData<List<GroupMemberEntry.FullMember>>      visibleSharedGroups;
  private final LiveData<String>                                 sharedGroupsCountSummary;
  private final LiveData<Boolean>                                canAddToAGroup;

  private ManageRecipientViewModel(@NonNull Context context, @NonNull ManageRecipientRepository manageRecipientRepository) {
    this.context                   = context;
    this.manageRecipientRepository = manageRecipientRepository;

    manageRecipientRepository.getThreadId(this::onThreadIdLoaded);

    this.recipient = Recipient.live(manageRecipientRepository.getRecipientId()).getLiveData();
    this.title     = Transformations.map(recipient, r -> getDisplayTitle(r, context));
    this.subtitle  = Transformations.map(recipient, r -> getDisplaySubtitle(r, context));
    this.identity  = new MutableLiveData<>();

    LiveData<List<Recipient>> allSharedGroups = LiveDataUtil.mapAsync(this.recipient, r -> manageRecipientRepository.getSharedGroups(r.getId()));

    this.sharedGroupsCountSummary = Transformations.map(allSharedGroups, list -> {
      int size = list.size();
      return size == 0 ? context.getString(R.string.ManageRecipientActivity_no_groups_in_common)
                       : context.getResources().getQuantityString(R.plurals.ManageRecipientActivity_d_groups_in_common, size, size);
    });

    this.canCollapseMemberList = LiveDataUtil.combineLatest(this.groupListCollapseState,
                                                            Transformations.map(allSharedGroups, m -> m.size() > MAX_COLLAPSED_GROUPS),
                                                            (state, hasEnoughMembers) -> state != CollapseState.OPEN && hasEnoughMembers);
    this.visibleSharedGroups   = Transformations.map(LiveDataUtil.combineLatest(allSharedGroups,
                                                     this.groupListCollapseState,
                                                     ManageRecipientViewModel::filterSharedGroupList),
                                                     recipients -> Stream.of(recipients).map(r -> new GroupMemberEntry.FullMember(r, false)).toList());

    this.disappearingMessageTimer = Transformations.map(this.recipient, r -> ExpirationUtil.getExpirationDisplayValue(context, r.getExpireMessages()));
    this.muteState                = Transformations.map(this.recipient, r -> new MuteState(r.getMuteUntil(), r.isMuted()));
    this.hasCustomNotifications   = Transformations.map(this.recipient, r -> r.getNotificationChannel() != null || !NotificationChannels.supported());
    this.canBlock                 = Transformations.map(this.recipient, r -> !r.isBlocked());

    boolean isSelf = manageRecipientRepository.getRecipientId().equals(Recipient.self().getId());
    if (!isSelf) {
      manageRecipientRepository.getIdentity(identity::postValue);
    }

    MutableLiveData<Integer> localGroupCount = new MutableLiveData<>(0);

    canAddToAGroup = LiveDataUtil.combineLatest(recipient, localGroupCount,
                                                (r, count) -> count > 0 && r.isRegistered() && !r.isGroup() && !r.isLocalNumber());

    manageRecipientRepository.getActiveGroupCount(localGroupCount::postValue);
  }

  private static @NonNull String getDisplayTitle(@NonNull Recipient recipient, @NonNull Context context) {
    if (recipient.isLocalNumber()) {
      return context.getString(R.string.note_to_self);
    } else {
      return recipient.getDisplayName(context);
    }
  }

  private static @NonNull String getDisplaySubtitle(@NonNull Recipient recipient, @NonNull Context context) {
    if (!recipient.isLocalNumber() && recipient.hasAUserSetDisplayName(context)) {
      return String.format("%s %s", recipient.getUsername().or(""), recipient.getSmsAddress().or(""))
                   .trim();
    } else {
      return "";
    }
  }

  @WorkerThread
  private void onThreadIdLoaded(long threadId) {
    mediaCursor.postValue(new MediaCursor(threadId,
                                          () -> new ThreadMediaLoader(context, threadId, MediaLoader.MediaType.GALLERY, MediaDatabase.Sorting.Newest).getCursor()));
  }

  LiveData<String> getTitle() {
    return title;
  }

  LiveData<String> getSubtitle() {
    return subtitle;
  }

  LiveData<Recipient> getRecipient() {
    return recipient;
  }

  LiveData<Boolean> getCanAddToAGroup() {
    return canAddToAGroup;
  }

  LiveData<MediaCursor> getMediaCursor() {
    return mediaCursor;
  }

  LiveData<MuteState> getMuteState() {
    return muteState;
  }

  LiveData<String> getDisappearingMessageTimer() {
    return disappearingMessageTimer;
  }

  LiveData<Boolean> hasCustomNotifications() {
    return hasCustomNotifications;
  }

  LiveData<Boolean> getCanCollapseMemberList() {
    return canCollapseMemberList;
  }

  LiveData<Boolean> getCanBlock() {
    return canBlock;
  }

  void handleExpirationSelection(@NonNull Context context) {
    withRecipient(recipient ->
                  ExpirationDialog.show(context,
                                        recipient.getExpireMessages(),
                                        manageRecipientRepository::setExpiration));
  }

  void setMuteUntil(long muteUntil) {
    manageRecipientRepository.setMuteUntil(muteUntil);
  }

  void clearMuteUntil() {
    manageRecipientRepository.setMuteUntil(0);
  }

  void revealCollapsedMembers() {
    groupListCollapseState.setValue(CollapseState.OPEN);
  }

  void onAddToGroupButton(@NonNull Activity activity) {
    manageRecipientRepository.getGroupMembership(existingGroups -> Util.runOnMain(() -> activity.startActivity(AddToGroupsActivity.newIntent(activity, manageRecipientRepository.getRecipientId(), existingGroups))));
  }

  private void withRecipient(@NonNull Consumer<Recipient> mainThreadRecipientCallback) {
    manageRecipientRepository.getRecipient(recipient -> Util.runOnMain(() -> mainThreadRecipientCallback.accept(recipient)));
  }

  private static @NonNull List<Recipient> filterSharedGroupList(@NonNull List<Recipient> groups,
                                                                @NonNull CollapseState collapseState)
  {
    if (collapseState == CollapseState.COLLAPSED && groups.size() > MAX_COLLAPSED_GROUPS) {
      return groups.subList(0, MAX_COLLAPSED_GROUPS);
    } else {
      return groups;
    }
  }

  LiveData<IdentityDatabase.IdentityRecord> getIdentity() {
    return identity;
  }

  void onBlockClicked(@NonNull FragmentActivity activity) {
    withRecipient(recipient -> BlockUnblockDialog.showBlockFor(activity, activity.getLifecycle(), recipient, () -> RecipientUtil.blockNonGroup(context, recipient)));
  }

  void onUnblockClicked(@NonNull FragmentActivity activity) {
    withRecipient(recipient -> BlockUnblockDialog.showUnblockFor(activity, activity.getLifecycle(), recipient, () -> RecipientUtil.unblock(context, recipient)));
  }

  void onViewSafetyNumberClicked(@NonNull Activity activity, @NonNull IdentityDatabase.IdentityRecord identityRecord) {
    activity.startActivity(VerifyIdentityActivity.newIntent(activity, identityRecord));
  }

  LiveData<List<GroupMemberEntry.FullMember>> getVisibleSharedGroups() {
    return visibleSharedGroups;
  }

  LiveData<String> getSharedGroupsCountSummary() {
    return sharedGroupsCountSummary;
  }

  void onSelectColor(int color) {
   manageRecipientRepository.setColor(color);
  }

  void onGroupClicked(@NonNull Activity activity, @NonNull Recipient recipient) {
    CommunicationActions.startConversation(activity, recipient, null);
    activity.finish();
  }

  void onMessage(@NonNull FragmentActivity activity) {
    withRecipient(r -> {
      CommunicationActions.startConversation(activity, r, null);
      activity.finish();
    });
  }

  void onSecureCall(@NonNull FragmentActivity activity) {
    withRecipient(r -> CommunicationActions.startVoiceCall(activity, r));
  }

  void onInsecureCall(@NonNull FragmentActivity activity) {
    withRecipient(r -> CommunicationActions.startInsecureCall(activity, r));
  }

  void onSecureVideoCall(@NonNull FragmentActivity activity) {
    withRecipient(r -> CommunicationActions.startVideoCall(activity, r));
  }

  void onAddedToContacts() {
    manageRecipientRepository.refreshRecipient();
  }

  void onFinishedViewingContact() {
    manageRecipientRepository.refreshRecipient();
  }

  static final class MediaCursor {
             private final long          threadId;
    @NonNull private final CursorFactory mediaCursorFactory;

    private MediaCursor(long threadId,
                        @NonNull CursorFactory mediaCursorFactory)
    {
      this.threadId           = threadId;
      this.mediaCursorFactory = mediaCursorFactory;
    }

    long getThreadId() {
      return threadId;
    }

    @NonNull CursorFactory getMediaCursorFactory() {
      return mediaCursorFactory;
    }
  }

  static final class MuteState {
    private final long    mutedUntil;
    private final boolean isMuted;

    MuteState(long mutedUntil, boolean isMuted) {
      this.mutedUntil = mutedUntil;
      this.isMuted    = isMuted;
    }

    long getMutedUntil() {
      return mutedUntil;
    }

    public boolean isMuted() {
      return isMuted;
    }
  }

  private enum CollapseState {
    OPEN,
    COLLAPSED
  }

  interface CursorFactory {
    Cursor create();
  }

  public static class Factory implements ViewModelProvider.Factory {
    private final Context     context;
    private final RecipientId recipientId;

    public Factory(@NonNull RecipientId recipientId) {
      this.context     = ApplicationDependencies.getApplication();
      this.recipientId = recipientId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new ManageRecipientViewModel(context, new ManageRecipientRepository(context, recipientId));
    }
  }
}
