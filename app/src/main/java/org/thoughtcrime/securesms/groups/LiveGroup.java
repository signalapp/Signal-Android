package org.thoughtcrime.securesms.groups;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Comparator;
import java.util.List;

public final class LiveGroup extends MediatorLiveData<GroupDatabase.GroupRecord> {

  private static final Comparator<GroupMemberEntry.FullMember>         LOCAL_FIRST  = (m1, m2) -> Boolean.compare(m2.getMember().isLocalNumber(), m1.getMember().isLocalNumber());
  private static final Comparator<GroupMemberEntry.FullMember>         ADMIN_FIRST  = (m1, m2) -> Boolean.compare(m2.isAdmin(), m1.isAdmin());
  private static final Comparator<? super GroupMemberEntry.FullMember> MEMBER_ORDER = ComparatorCompat.chain(LOCAL_FIRST)
                                                                                                      .thenComparing(ADMIN_FIRST);

  private final GroupDatabase       groupDatabase;
  private final LiveData<Recipient> recipient;

  public LiveGroup(@NonNull GroupId groupId) {
    Context context = ApplicationDependencies.getApplication();

    this.groupDatabase = DatabaseFactory.getGroupDatabase(context);

    recipient = Recipient.externalGroup(context, groupId).live().getLiveData();

    addSource(recipient, this::refresh);
  }

  private void refresh(@NonNull Recipient groupRecipient) {
    SignalExecutors.BOUNDED.execute(() -> {
        Optional<GroupDatabase.GroupRecord> group = groupDatabase.getGroup(groupRecipient.getId());
        if (group.isPresent()) {
          postValue(group.get());
        }
      }
    );
  }

  public LiveData<String> getTitle() {
    return Transformations.map(this, GroupDatabase.GroupRecord::getTitle);
  }

  public LiveData<Boolean> isSelfAdmin() {
    return Transformations.map(this, g -> g.isAdmin(Recipient.self()));
  }

  public LiveData<Boolean> getRecipientIsAdmin(@NonNull RecipientId recipientId) {
    return LiveDataUtil.mapAsync(this, g -> g.isAdmin(Recipient.resolved(recipientId)));
  }

  public LiveData<Integer> getPendingMemberCount() {
    return Transformations.map(this, g -> g.isV2Group() ? g.requireV2GroupProperties().getDecryptedGroup().getPendingMembersCount() : 0);
  }

  public LiveData<GroupAccessControl> getMembershipAdditionAccessControl() {
    return Transformations.map(this, GroupDatabase.GroupRecord::getMembershipAdditionAccessControl);
  }

  public LiveData<GroupAccessControl> getAttributesAccessControl() {
    return Transformations.map(this, GroupDatabase.GroupRecord::getAttributesAccessControl);
  }

  public LiveData<List<GroupMemberEntry.FullMember>> getFullMembers() {
     return LiveDataUtil.mapAsync(this,
                                  g -> Stream.of(g.getMembers())
                                             .map(m -> {
                                               Recipient recipient = Recipient.resolved(m);
                                               return new GroupMemberEntry.FullMember(recipient, g.isAdmin(recipient));
                                             })
                                             .sorted(MEMBER_ORDER)
                                             .toList());
  }

  public LiveData<Integer> getExpireMessages() {
    return Transformations.map(recipient, Recipient::getExpireMessages);
  }

  public LiveData<Boolean> selfCanEditGroupAttributes() {
    return LiveDataUtil.combineLatest(isSelfAdmin(),
                                      getAttributesAccessControl(),
                                      (admin, rights) -> {
                                          switch (rights) {
                                            case ALL_MEMBERS:
                                              return true;
                                            case ONLY_ADMINS:
                                              return admin;
                                            default:
                                              throw new AssertionError();
                                          }
                                        }
                                      );
  }

  public LiveData<String> getMembershipCountDescription(@NonNull Resources resources) {
    return LiveDataUtil.combineLatest(getFullMembers(),
                                      getPendingMemberCount(),
                                      (fullMembers, invitedCount) -> getMembershipDescription(resources, invitedCount, fullMembers.size()));
  }

  private static String getMembershipDescription(@NonNull Resources resources, int invitedCount, int fullMemberCount) {
    return invitedCount > 0 ? resources.getQuantityString(R.plurals.MessageRequestProfileView_members_and_invited, fullMemberCount,
                                                          fullMemberCount, invitedCount)
                            : resources.getQuantityString(R.plurals.MessageRequestProfileView_members, fullMemberCount,
                                                          fullMemberCount);
  }
}
