package org.thoughtcrime.securesms.database.model;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class GroupsV2UpdateMessageProducerTest {

  private UUID you;
  private UUID alice;
  private UUID bob;

  private GroupsV2UpdateMessageProducer producer;

  @Before
  public void setup() {
    you   = UUID.randomUUID();
    alice = UUID.randomUUID();
    bob   = UUID.randomUUID();
    GroupsV2UpdateMessageProducer.DescribeMemberStrategy describeMember = createDescriber(ImmutableMap.of(alice, "Alice", bob, "Bob"));
    producer = new GroupsV2UpdateMessageProducer(ApplicationProvider.getApplicationContext(), describeMember, you);
  }

  @Test
  public void empty_change() {
    DecryptedGroupChange change = changeBy(alice)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice updated the group.")));
  }

  @Test
  public void empty_change_by_you() {
    DecryptedGroupChange change = changeBy(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You updated the group.")));
  }

  @Test
  public void empty_change_by_unknown() {
    DecryptedGroupChange change = changeByUnknown()
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("The group was updated.")));
  }

  // Member additions

  @Test
  public void member_added_member() {
    DecryptedGroupChange change = changeBy(alice)
                                    .addMember(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice added Bob.")));
  }

  @Test
  public void you_added_member() {
    DecryptedGroupChange change = changeBy(you)
                                    .addMember(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You added Bob.")));
  }

  @Test
  public void member_added_you() {
    DecryptedGroupChange change = changeBy(alice)
                                    .addMember(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice added you to the group.")));
  }

  @Test
  public void you_added_you() {
    DecryptedGroupChange change = changeBy(you)
                                    .addMember(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You joined the group.")));
  }

  @Test
  public void member_added_themselves() {
    DecryptedGroupChange change = changeBy(bob)
                                    .addMember(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Bob joined the group.")));
  }

  @Test
  public void unknown_added_you() {
    DecryptedGroupChange change = changeByUnknown()
                                    .addMember(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You joined the group.")));
  }

  @Test
  public void unknown_added_member() {
    DecryptedGroupChange change = changeByUnknown()
                                    .addMember(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Bob joined the group.")));
  }

  // Member removals

  @Test
  public void member_removed_member() {
    DecryptedGroupChange change = changeBy(alice)
                                    .deleteMember(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice removed Bob.")));
  }

  @Test
  public void you_removed_member() {
    DecryptedGroupChange change = changeBy(you)
                                    .deleteMember(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You removed Bob.")));
  }

  @Test
  public void member_removed_you() {
    DecryptedGroupChange change = changeBy(alice)
                                    .deleteMember(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice removed you from the group.")));
  }

  @Test
  public void you_removed_you() {
    DecryptedGroupChange change = changeBy(you)
                                    .deleteMember(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You left the group.")));
  }

  @Test
  public void member_removed_themselves() {
    DecryptedGroupChange change = changeBy(bob)
                                    .deleteMember(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Bob left the group.")));
  }

  @Test
  public void unknown_removed_member() {
    DecryptedGroupChange change = changeByUnknown()
                                    .deleteMember(alice)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice is no longer in the group.")));
  }

  @Test
  public void unknown_removed_you() {
    DecryptedGroupChange change = changeByUnknown()
                                    .deleteMember(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You are no longer in the group.")));
  }

  // Member role modifications

  @Test
  public void you_make_member_admin() {
    DecryptedGroupChange change = changeBy(you)
                                    .promoteToAdmin(alice)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You made Alice an admin.")));
  }

  @Test
  public void member_makes_member_admin() {
    DecryptedGroupChange change = changeBy(bob)
                                    .promoteToAdmin(alice)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Bob made Alice an admin.")));
  }

  @Test
  public void member_makes_you_admin() {
    DecryptedGroupChange change = changeBy(alice)
                                    .promoteToAdmin(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice made you an admin.")));
  }

  @Test
  public void you_revoked_member_admin() {
    DecryptedGroupChange change = changeBy(you)
                                    .demoteToMember(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You revoked admin privileges from Bob.")));
  }

  @Test
  public void member_revokes_member_admin() {
    DecryptedGroupChange change = changeBy(bob)
                                    .demoteToMember(alice)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Bob revoked admin privileges from Alice.")));
  }

  @Test
  public void member_revokes_your_admin() {
    DecryptedGroupChange change = changeBy(alice)
                                    .demoteToMember(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice revoked your admin privileges.")));
  }

  @Test
  public void unknown_makes_member_admin() {
    DecryptedGroupChange change = changeByUnknown()
                                    .promoteToAdmin(alice)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice is now an admin.")));
  }

  @Test
  public void unknown_makes_you_admin() {
    DecryptedGroupChange change = changeByUnknown()
                                    .promoteToAdmin(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You are now an admin.")));
  }

  @Test
  public void unknown_revokes_member_admin() {
    DecryptedGroupChange change = changeByUnknown()
                                    .demoteToMember(alice)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice is no longer an admin.")));
  }

  @Test
  public void unknown_revokes_your_admin() {
    DecryptedGroupChange change = changeByUnknown()
                                    .demoteToMember(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You are no longer an admin.")));
  }

  // Member invitation

  @Test
  public void you_invited_member() {
    DecryptedGroupChange change = changeBy(you)
                                    .invite(alice)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You invited Alice to the group.")));
  }

  @Test
  public void member_invited_you() {
    DecryptedGroupChange change = changeBy(alice)
                                    .invite(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice invited you to the group.")));
  }

  @Test
  public void member_invited_1_person() {
    DecryptedGroupChange change = changeBy(alice)
                                    .invite(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice invited 1 person to the group.")));
  }

  @Test
  public void member_invited_2_persons() {
    DecryptedGroupChange change = changeBy(alice)
                                    .invite(bob)
                                    .invite(UUID.randomUUID())
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice invited 2 people to the group.")));
  }

  @Test
  public void member_invited_3_persons_and_you() {
    DecryptedGroupChange change = changeBy(bob)
                                    .invite(alice)
                                    .invite(you)
                                    .invite(UUID.randomUUID())
                                    .invite(UUID.randomUUID())
                                    .build();

    assertThat(producer.describeChange(change), is(Arrays.asList("Bob invited you to the group.", "Bob invited 3 people to the group.")));
  }

  @Test
  public void unknown_invited_you() {
    DecryptedGroupChange change = changeByUnknown()
                                    .invite(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You were invited to the group.")));
  }

  @Test
  public void unknown_invited_1_person() {
    DecryptedGroupChange change = changeByUnknown()
                                    .invite(alice)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("1 person was invited to the group.")));
  }

  @Test
  public void unknown_invited_2_persons() {
    DecryptedGroupChange change = changeByUnknown()
                                    .invite(alice)
                                    .invite(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("2 people were invited to the group.")));
  }

  @Test
  public void unknown_invited_3_persons_and_you() {
    DecryptedGroupChange change = changeByUnknown()
                                    .invite(alice)
                                    .invite(you)
                                    .invite(UUID.randomUUID())
                                    .invite(UUID.randomUUID())
                                    .build();

    assertThat(producer.describeChange(change), is(Arrays.asList("You were invited to the group.", "3 people were invited to the group.")));
  }

  // Member invitation revocation

  @Test
  public void member_uninvited_1_person() {
    DecryptedGroupChange change = changeBy(alice)
                                    .uninvite(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice revoked an invitation to the group.")));
  }

  @Test
  public void member_uninvited_2_people() {
    DecryptedGroupChange change = changeBy(alice)
                                    .uninvite(bob)
                                    .uninvite(UUID.randomUUID())
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice revoked 2 invitations to the group.")));
  }

  @Test
  public void you_uninvited_1_person() {
    DecryptedGroupChange change = changeBy(you)
                                    .uninvite(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You revoked an invitation to the group.")));
  }

  @Test
  public void you_uninvited_2_people() {
    DecryptedGroupChange change = changeBy(you)
                                    .uninvite(bob)
                                    .uninvite(UUID.randomUUID())
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You revoked 2 invitations to the group.")));
  }

  @Test
  public void pending_member_declines_invite() {
    DecryptedGroupChange change = changeBy(bob)
                                    .uninvite(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Someone declined an invitation to the group.")));
  }

  @Test
  public void you_decline_invite() {
    DecryptedGroupChange change = changeBy(you)
                                    .uninvite(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You declined the invitation to the group.")));
  }

  @Test
  public void unknown_revokes_your_invite() {
    DecryptedGroupChange change = changeByUnknown()
                                    .uninvite(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Your invitation to the group was revoked.")));
  }

  @Test
  public void unknown_revokes_1_invite() {
    DecryptedGroupChange change = changeByUnknown()
                                    .uninvite(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("An invitation to the group was revoked.")));
  }

  @Test
  public void unknown_revokes_2_invites() {
    DecryptedGroupChange change = changeByUnknown()
                                    .uninvite(bob)
                                    .uninvite(UUID.randomUUID())
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("2 invitations to the group were revoked.")));
  }

  @Test
  public void unknown_revokes_yours_and_three_other_invites() {
    DecryptedGroupChange change = changeByUnknown()
                                    .uninvite(bob)
                                    .uninvite(you)
                                    .uninvite(UUID.randomUUID())
                                    .uninvite(UUID.randomUUID())
                                    .build();

    assertThat(producer.describeChange(change), is(Arrays.asList("Your invitation to the group was revoked.", "3 invitations to the group were revoked.")));
  }

  // Promote pending members

  @Test
  public void member_accepts_invite() {
    DecryptedGroupChange change = changeBy(bob)
                                    .promote(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Bob accepted an invitation to the group.")));
  }

  @Test
  public void you_accept_invite() {
    DecryptedGroupChange change = changeBy(you)
                                    .promote(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You accepted the invitation to the group.")));
  }

  @Test
  public void member_promotes_pending_member() {
    DecryptedGroupChange change = changeBy(bob)
                                    .promote(alice)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Bob added invited member Alice.")));
  }

  @Test
  public void you_promote_pending_member() {
    DecryptedGroupChange change = changeBy(you)
                                    .promote(bob)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You added invited member Bob.")));
  }

  @Test
  public void member_promotes_you() {
    DecryptedGroupChange change = changeBy(bob)
                                    .promote(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Bob added you to the group.")));
  }

  @Test
  public void unknown_added_by_invite() {
    DecryptedGroupChange change = changeByUnknown()
                                    .promote(you)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You joined the group.")));
  }

  @Test
  public void unknown_promotes_pending_member() {
    DecryptedGroupChange change = changeByUnknown()
                                    .promote(alice)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice joined the group.")));
  }

  // Title change

  @Test
  public void member_changes_title() {
    DecryptedGroupChange change = changeBy(alice)
                                    .title("New title")
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice changed the group name to \"New title\".")));
  }

  @Test
  public void you_change_title() {
    DecryptedGroupChange change = changeBy(you)
                                    .title("Title 2")
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You changed the group name to \"Title 2\".")));
  }

  @Test
  public void unknown_changed_title() {
    DecryptedGroupChange change = changeByUnknown()
                                    .title("Title 3")
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("The group name has changed to \"Title 3\".")));
  }
  
  // Avatar change

  @Test
  public void member_changes_avatar() {
    DecryptedGroupChange change = changeBy(alice)
                                    .avatar("Avatar1")
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice changed the group avatar.")));
  }

  @Test
  public void you_change_avatar() {
    DecryptedGroupChange change = changeBy(you)
                                    .avatar("Avatar2")
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You changed the group avatar.")));
  }

  @Test
  public void unknown_changed_avatar() {
    DecryptedGroupChange change = changeByUnknown()
                                    .avatar("Avatar3")
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("The group avatar has been changed.")));
  }

  // Timer change

  @Test
  public void member_changes_timer() {
    DecryptedGroupChange change = changeBy(bob)
                                    .timer(10)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Bob set the disappearing message timer to 10 seconds.")));
  }

  @Test
  public void you_change_timer() {
    DecryptedGroupChange change = changeBy(you)
                                    .timer(60)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You set the disappearing message timer to 1 minute.")));
  }

  @Test
  public void unknown_change_timer() {
    DecryptedGroupChange change = changeByUnknown()
                                    .timer(120)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("The disappearing message timer has been set to 2 minutes.")));
  }

  // Attribute access change

  @Test
  public void member_changes_attribute_access() {
    DecryptedGroupChange change = changeBy(bob)
                                    .attributeAccess(AccessControl.AccessRequired.MEMBER)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Bob changed who can edit group info to \"All members\".")));
  }

  @Test
  public void you_changed_attribute_access() {
    DecryptedGroupChange change = changeBy(you)
                                    .attributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You changed who can edit group info to \"Only admins\".")));
  }

  @Test
  public void unknown_changed_attribute_access() {
    DecryptedGroupChange change = changeByUnknown()
                                    .attributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Who can edit group info has been changed to \"Only admins\".")));
  }

  // Membership access change

  @Test
  public void member_changes_membership_access() {
    DecryptedGroupChange change = changeBy(alice)
                                    .membershipAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Alice changed who can edit group membership to \"Only admins\".")));
  }

  @Test
  public void you_changed_membership_access() {
    DecryptedGroupChange change = changeBy(you)
                                    .membershipAccess(AccessControl.AccessRequired.MEMBER)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("You changed who can edit group membership to \"All members\".")));
  }

  @Test
  public void unknown_changed_membership_access() {
    DecryptedGroupChange change = changeByUnknown()
                                    .membershipAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .build();

    assertThat(producer.describeChange(change), is(singletonList("Who can edit group membership has been changed to \"Only admins\".")));
  }

  // Multiple changes

  @Test
  public void multiple_changes() {
    DecryptedGroupChange change = changeBy(alice)
                                    .addMember(bob)
                                    .membershipAccess(AccessControl.AccessRequired.MEMBER)
                                    .title("Title")
                                    .timer(300)
                                    .build();

    assertThat(producer.describeChange(change), is(Arrays.asList(
      "Alice added Bob.",
      "Alice changed the group name to \"Title\".",
      "Alice set the disappearing message timer to 5 minutes.",
      "Alice changed who can edit group membership to \"All members\".")));
  }

  @Test
  public void multiple_changes_by_unknown() {
    DecryptedGroupChange change = changeByUnknown()
                                    .addMember(bob)
                                    .membershipAccess(AccessControl.AccessRequired.MEMBER)
                                    .title("Title 2")
                                    .avatar("Avatar 1")
                                    .timer(600)
                                    .build();

    assertThat(producer.describeChange(change), is(Arrays.asList(
      "Bob joined the group.",
      "The group name has changed to \"Title 2\".",
      "The group avatar has been changed.",
      "The disappearing message timer has been set to 10 minutes.",
      "Who can edit group membership has been changed to \"All members\".")));
  }

  // Group state without a change record

  @Test
  public void you_created_a_group() {
    DecryptedGroup group = newGroupBy(you, 0)
                             .build();

    assertThat(producer.describeNewGroup(group), is("You created the group."));
  }

  @Test
  public void alice_created_a_group() {
    DecryptedGroup group = newGroupBy(alice, 0)
                             .member(you)
                             .build();

    assertThat(producer.describeNewGroup(group), is("Alice added you to the group."));
  }

  @Test
  public void alice_created_a_group_above_zero() {
    DecryptedGroup group = newGroupBy(alice, 1)
                             .member(you)
                             .build();

    assertThat(producer.describeNewGroup(group), is("You joined the group."));
  }

  @Test
  public void you_were_invited_to_a_group() {
    DecryptedGroup group = newGroupBy(alice, 0)
                             .invite(bob, you)
                             .build();

    assertThat(producer.describeNewGroup(group), is("Bob invited you to the group."));
  }

  @Test
  public void describe_a_group_you_are_not_in() {
    DecryptedGroup group = newGroupBy(alice, 1)
                             .build();

    assertThat(producer.describeNewGroup(group), is("Group updated."));
  }

  private GroupStateBuilder newGroupBy(UUID foundingMember, int revision) {
    return new GroupStateBuilder(foundingMember, revision);
  }

  private static class GroupStateBuilder {

    private final DecryptedGroup.Builder builder;

    GroupStateBuilder(@NonNull UUID foundingMember, int revision) {
    builder = DecryptedGroup.newBuilder()
                            .setRevision(revision)
                            .addMembers(DecryptedMember.newBuilder()
                                                       .setUuid(UuidUtil.toByteString(foundingMember)));
    }

    GroupStateBuilder invite(@NonNull UUID inviter, @NonNull UUID invitee) {
       builder.addPendingMembers(DecryptedPendingMember.newBuilder()
                                                       .setUuid(UuidUtil.toByteString(invitee))
                                                       .setAddedByUuid(UuidUtil.toByteString(inviter)));
       return this;
    }

    GroupStateBuilder member(@NonNull UUID member) {
       builder.addMembers(DecryptedMember.newBuilder()
                                         .setUuid(UuidUtil.toByteString(member)));
       return this;
    }

    public DecryptedGroup build() {
      return builder.build();
    }
  }

  private static class ChangeBuilder {

    private final DecryptedGroupChange.Builder builder;

    ChangeBuilder(@NonNull UUID editor) {
      builder = DecryptedGroupChange.newBuilder()
                                    .setEditor(UuidUtil.toByteString(editor));
    }

    ChangeBuilder() {
      builder = DecryptedGroupChange.newBuilder();
    }

    ChangeBuilder addMember(@NonNull UUID newMember) {
      builder.addNewMembers(DecryptedMember.newBuilder()
                                           .setUuid(UuidUtil.toByteString(newMember)));
      return this;
    }

    ChangeBuilder deleteMember(@NonNull UUID removedMember) {
      builder.addDeleteMembers(UuidUtil.toByteString(removedMember));
      return this;
    }

    ChangeBuilder promoteToAdmin(@NonNull UUID member) {
      builder.addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
                                                            .setRole(Member.Role.ADMINISTRATOR)
                                                            .setUuid(UuidUtil.toByteString(member)));
      return this;
    }

    ChangeBuilder demoteToMember(@NonNull UUID member) {
      builder.addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
                                                            .setRole(Member.Role.DEFAULT)
                                                            .setUuid(UuidUtil.toByteString(member)));
      return this;
    }

    ChangeBuilder invite(@NonNull UUID potentialMember) {
      builder.addNewPendingMembers(DecryptedPendingMember.newBuilder()
                                                         .setUuid(UuidUtil.toByteString(potentialMember)));
      return this;
    }

    ChangeBuilder uninvite(@NonNull UUID pendingMember) {
      builder.addDeletePendingMembers(DecryptedPendingMemberRemoval.newBuilder()
                                                                   .setUuid(UuidUtil.toByteString(pendingMember)));
      return this;
    }

    ChangeBuilder promote(@NonNull UUID pendingMember) {
      builder.addPromotePendingMembers(DecryptedMember.newBuilder().setUuid(UuidUtil.toByteString(pendingMember)));
      return this;
    }

    ChangeBuilder title(@NonNull String newTitle) {
      builder.setNewTitle(DecryptedString.newBuilder()
                                         .setValue(newTitle));
      return this;
    }

    ChangeBuilder avatar(@NonNull String newAvatar) {
      builder.setNewAvatar(DecryptedString.newBuilder()
                                          .setValue(newAvatar));
      return this;
    }

    ChangeBuilder timer(int duration) {
      builder.setNewTimer(DecryptedTimer.newBuilder()
                                        .setDuration(duration));
      return this;
    }

    ChangeBuilder attributeAccess(@NonNull AccessControl.AccessRequired accessRequired) {
      builder.setNewAttributeAccess(accessRequired);
      return this;
    }

    ChangeBuilder membershipAccess(@NonNull AccessControl.AccessRequired accessRequired) {
      builder.setNewMemberAccess(accessRequired);
      return this;
    }

    DecryptedGroupChange build() {
      return builder.build();
    }
  }

  private static ChangeBuilder changeBy(@NonNull UUID groupEditor) {
    return new ChangeBuilder(groupEditor);
  }

  private static ChangeBuilder changeByUnknown() {
    return new ChangeBuilder();
  }

  private static @NonNull GroupsV2UpdateMessageProducer.DescribeMemberStrategy createDescriber(@NonNull Map<UUID, String> map) {
    return uuid -> {
      String name = map.get(uuid);
      assertNotNull(name);
      return name;
    };
  }
}
