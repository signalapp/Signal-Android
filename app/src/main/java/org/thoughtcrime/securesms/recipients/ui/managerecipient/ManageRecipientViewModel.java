package org.thoughtcrime.securesms.recipients.ui.managerecipient;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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

import org.signal.core.util.ThreadUtil;
import org.thoughtcrime.securesms.DialogWithListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.components.Mp02CustomDialog;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.MediaDatabase;
import org.thoughtcrime.securesms.database.loaders.MediaLoader;
import org.thoughtcrime.securesms.database.loaders.ThreadMediaLoader;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.groups.ui.addtogroup.AddToGroupsActivity;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.UUID;

import static org.thoughtcrime.securesms.conversation.ConversationActivity.RECIPIENT_EXTRA;
import static org.thoughtcrime.securesms.conversation.ConversationActivity.STRING_CURRENT_EXPIRATION;

public final class ManageRecipientViewModel extends ViewModel {

  private static final int MAX_UNCOLLAPSED_GROUPS = 6;
  private static final int SHOW_COLLAPSED_GROUPS  = 5;

  private final Context                                          context;
  private final ManageRecipientRepository                        manageRecipientRepository;
  private final LiveData<String>                                 title;
  private final LiveData<String>                                 subtitle;
  private final LiveData<String>                                 internalDetails;
  private final LiveData<String>                                 disappearingMessageTimer;
  private final MutableLiveData<IdentityDatabase.IdentityRecord> identity;
  private final LiveData<Recipient>                              recipient;
  private final MutableLiveData<MediaCursor>                     mediaCursor;
  private final LiveData<MuteState>                              muteState;
  private final LiveData<Boolean>                                hasCustomNotifications;
  private final LiveData<Boolean>                                canCollapseMemberList;
  private final DefaultValueLiveData<CollapseState>              groupListCollapseState;
  private final LiveData<Boolean>                                canBlock;
  private final LiveData<Boolean>                                canUnblock;
  private final LiveData<List<GroupMemberEntry.FullMember>>      visibleSharedGroups;
  private final LiveData<String>                                 sharedGroupsCountSummary;
  private final LiveData<Boolean>                                canAddToAGroup;

  private ManageRecipientViewModel(@NonNull Context context, @NonNull ManageRecipientRepository manageRecipientRepository) {
    this.context                   = context;
    this.manageRecipientRepository = manageRecipientRepository;
    this.recipient                 = Recipient.live(manageRecipientRepository.getRecipientId()).getLiveData();
    this.title                     = Transformations.map(recipient, r -> getDisplayTitle(r, context)   );
    this.subtitle                  = Transformations.map(recipient, r -> getDisplaySubtitle(r, context));
    this.identity                  = new MutableLiveData<>();
    this.mediaCursor               = new MutableLiveData<>(null);
    this.groupListCollapseState    = new DefaultValueLiveData<>(CollapseState.COLLAPSED);
    this.disappearingMessageTimer  = Transformations.map(this.recipient, r -> ExpirationUtil.getExpirationDisplayValue(context, r.getExpiresInSeconds()));
    this.muteState                 = Transformations.map(this.recipient, r -> new MuteState(r.getMuteUntil(), r.isMuted()));
    this.hasCustomNotifications    = LiveDataUtil.mapAsync(this.recipient, manageRecipientRepository::hasCustomNotifications);
    this.canBlock                  = Transformations.map(this.recipient, r -> RecipientUtil.isBlockable(r) && !r.isBlocked());
    this.canUnblock                = Transformations.map(this.recipient, Recipient::isBlocked);
    this.internalDetails           = Transformations.map(this.recipient, this::populateInternalDetails);

    manageRecipientRepository.getThreadId(this::onThreadIdLoaded);

    LiveData<List<Recipient>> allSharedGroups = LiveDataUtil.mapAsync(this.recipient, r -> manageRecipientRepository.getSharedGroups(r.getId()));

    this.sharedGroupsCountSummary = Transformations.map(allSharedGroups, list -> {
      int size = list.size();
      return size == 0 ? context.getString(R.string.ManageRecipientActivity_no_groups_in_common)
                       : context.getResources().getQuantityString(R.plurals.ManageRecipientActivity_d_groups_in_common, size, size);
    });

    this.canCollapseMemberList = LiveDataUtil.combineLatest(this.groupListCollapseState,
                                                            Transformations.map(allSharedGroups, m -> m.size() > MAX_UNCOLLAPSED_GROUPS),
                                                            (state, hasEnoughMembers) -> state != CollapseState.OPEN && hasEnoughMembers);
    this.visibleSharedGroups   = Transformations.map(LiveDataUtil.combineLatest(allSharedGroups,
                                                     this.groupListCollapseState,
                                                     ManageRecipientViewModel::filterSharedGroupList),
                                                     recipients -> Stream.of(recipients).map(r -> new GroupMemberEntry.FullMember(r, false)).toList());


    boolean isSelf = manageRecipientRepository.getRecipientId().equals(Recipient.self().getId());
    if (!isSelf) {
      manageRecipientRepository.getIdentity(identity::postValue);
    }

    MutableLiveData<Integer> localGroupCount = new MutableLiveData<>(0);

    this.canAddToAGroup = LiveDataUtil.combineLatest(recipient,
                                                     localGroupCount,
                                                     (r, count) -> count > 0 && r.isRegistered() && !r.isGroup() && !r.isSelf());

    manageRecipientRepository.getActiveGroupCount(localGroupCount::postValue);
  }

  private static @NonNull String getDisplayTitle(@NonNull Recipient recipient, @NonNull Context context) {
    if (recipient.isSelf()) {
      return context.getString(R.string.note_to_self);
    } else {
      return recipient.getDisplayName(context);
    }
  }

  private static @NonNull String getDisplaySubtitle(@NonNull Recipient recipient, @NonNull Context context) {
    if (!recipient.isSelf() && recipient.hasAUserSetDisplayName(context)) {
      return recipient.getSmsAddress().transform(PhoneNumberFormatter::prettyPrint).or("").trim();
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

  LiveData<String> getInternalDetails() {
    return internalDetails;
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

  LiveData<Boolean> getCanUnblock() {
    return canUnblock;
  }

  void handleExpirationSelection(@NonNull Context context) {
    boolean activeGroup = isActiveGroup();

    if (isPushGroupConversation() && !activeGroup) {
      return;
    }

    //final long thread = this.threadId;
    Intent intent = new Intent(context, DialogWithListActivity.class);
    intent.putExtra(STRING_CURRENT_EXPIRATION, recipient.getValue().getExpiresInSeconds());
    intent.putExtra(RECIPIENT_EXTRA, recipient.getValue().getId());
    //intent.putExtra(THREAD_ID_EXTRA, threadId);
    context.startActivity(intent);
  }

  private boolean isActiveGroup() {
    if (!isGroupConversation()) return false;

    Optional<GroupDatabase.GroupRecord> record = DatabaseFactory.getGroupDatabase(context).getGroup(Recipient.self().getId());
    return record.isPresent() && record.get().isActive();
  }

  private boolean isGroupConversation() {
    return Recipient.self() != null && Recipient.self().isGroup();
  }

  private boolean isPushGroupConversation() {
    return Recipient.self() != null && Recipient.self().isPushGroup();
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
    manageRecipientRepository.getGroupMembership(existingGroups -> ThreadUtil.runOnMain(() -> activity.startActivity(AddToGroupsActivity.newIntent(activity, manageRecipientRepository.getRecipientId(), existingGroups))));
  }

  private void withRecipient(@NonNull Consumer<Recipient> mainThreadRecipientCallback) {
    manageRecipientRepository.getRecipient(recipient -> ThreadUtil.runOnMain(() -> mainThreadRecipientCallback.accept(recipient)));
  }

  private static @NonNull List<Recipient> filterSharedGroupList(@NonNull List<Recipient> groups,
                                                                @NonNull CollapseState collapseState)
  {
    if (collapseState == CollapseState.COLLAPSED && groups.size() > MAX_UNCOLLAPSED_GROUPS) {
      return groups.subList(0, SHOW_COLLAPSED_GROUPS);
    } else {
      return groups;
    }
  }

  LiveData<IdentityDatabase.IdentityRecord> getIdentity() {
    return identity;
  }

  void onBlockClicked(@NonNull FragmentActivity activity) {
    Mp02CustomDialog dialog = new Mp02CustomDialog(activity);
    if (recipient.getValue().isGroup()) {
      if (DatabaseFactory.getGroupDatabase(context).isActive(recipient.getValue().requireGroupId())) {
        dialog.setMessage(activity.getString(R.string.BlockUnblockDialog_you_will_no_longer_receive_messages_or_updates));
        dialog.setPositiveListener(R.string.BlockUnblockDialog_block_and_leave, new Mp02CustomDialog.Mp02DialogKeyListener() {
          @Override
          public void onDialogKeyClicked() {
            RecipientUtil.blockNonGroup(context, recipient.getValue());
            dialog.dismiss();
          }
        });
        dialog.setNegativeListener(android.R.string.cancel, null);
      } else {
        dialog.setMessage(activity.getString(R.string.BlockUnblockDialog_group_members_wont_be_able_to_add_you));
        dialog.setPositiveListener(R.string.RecipientPreferenceActivity_block, new Mp02CustomDialog.Mp02DialogKeyListener() {
          @Override
          public void onDialogKeyClicked() {
            RecipientUtil.blockNonGroup(context, recipient.getValue());
            dialog.dismiss();
          }
        });
        dialog.setNegativeListener(android.R.string.cancel, null);
      }
    } else {
      dialog.setMessage(activity.getString(R.string.BlockUnblockDialog_blocked_people_wont_be_able_to_call_you_or_send_you_messages));
      dialog.setPositiveListener(R.string.BlockUnblockDialog_block, new Mp02CustomDialog.Mp02DialogKeyListener() {
        @Override
        public void onDialogKeyClicked() {
          RecipientUtil.blockNonGroup(context, recipient.getValue());
          dialog.dismiss();
        }
      });
      dialog.setNegativeListener(android.R.string.cancel, null);
    }
    dialog.show();
  }

  void onUnblockClicked(@NonNull FragmentActivity activity) {
    Mp02CustomDialog dialog = new Mp02CustomDialog(activity);
    if (recipient.getValue().isGroup()) {
      if (DatabaseFactory.getGroupDatabase(context).isActive(recipient.getValue().requireGroupId())) {
        dialog.setMessage(activity.getString(R.string.BlockUnblockDialog_group_members_will_be_able_to_add_you));
        dialog.setPositiveListener(R.string.RecipientPreferenceActivity_unblock, new Mp02CustomDialog.Mp02DialogKeyListener() {
          @Override
          public void onDialogKeyClicked() {
            RecipientUtil.unblock(context, recipient.getValue());
            dialog.dismiss();
          }
        });
        dialog.setNegativeListener(android.R.string.cancel, null);
      } else {
        dialog.setMessage(activity.getString(R.string.BlockUnblockDialog_group_members_will_be_able_to_add_you));
        dialog.setPositiveListener(R.string.RecipientPreferenceActivity_unblock, new Mp02CustomDialog.Mp02DialogKeyListener() {
          @Override
          public void onDialogKeyClicked() {
            RecipientUtil.unblock(context, recipient.getValue());
            dialog.dismiss();
          }
        });
        dialog.setNegativeListener(android.R.string.cancel, null);
      }
    } else {
      dialog.setMessage(activity.getString(R.string.BlockUnblockDialog_you_will_be_able_to_call_and_message_each_other));
      dialog.setPositiveListener(R.string.RecipientPreferenceActivity_unblock, new Mp02CustomDialog.Mp02DialogKeyListener() {
        @Override
        public void onDialogKeyClicked() {
          RecipientUtil.unblock(context, recipient.getValue());
          dialog.dismiss();
        }
      });
      dialog.setNegativeListener(android.R.string.cancel, null);
    }
    dialog.show();
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

  private @NonNull String populateInternalDetails(@NonNull Recipient recipient) {
    if (!SignalStore.internalValues().recipientDetails()) {
      return "";
    }

    String profileKeyBase64 = recipient.getProfileKey() != null ? Base64.encodeBytes(recipient.getProfileKey()) : "None";
    String profileKeyHex    = recipient.getProfileKey() != null ? Hex.toStringCondensed(recipient.getProfileKey()) : "None";
    return String.format("-- Profile Name --\n[%s] [%s]\n\n" +
                         "-- Profile Sharing --\n%s\n\n" +
                         "-- Profile Key (Base64) --\n%s\n\n" +
                         "-- Profile Key (Hex) --\n%s\n\n" +
                         "-- Sealed Sender Mode --\n%s\n\n" +
                         "-- UUID --\n%s\n\n" +
                         "-- RecipientId --\n%s",
                         recipient.getProfileName().getGivenName(), recipient.getProfileName().getFamilyName(),
                         recipient.isProfileSharing(),
                         profileKeyBase64,
                         profileKeyHex,
                         recipient.getUnidentifiedAccessMode(),
                         recipient.getUuid().transform(UUID::toString).or("None"),
                         recipient.getId().serialize());
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
