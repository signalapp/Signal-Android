package org.thoughtcrime.securesms.database.model;

import android.app.Application;
import android.text.Spannable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.annimon.stream.Stream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceIds;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.signal.core.util.StringUtil.isolateBidi;
import static org.thoughtcrime.securesms.groups.v2.ChangeBuilder.changeBy;
import static org.thoughtcrime.securesms.groups.v2.ChangeBuilder.changeByUnknown;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class GroupsV2UpdateMessageProducerTest {

  private UUID you;
  private UUID alice;
  private UUID bob;

  private GroupsV2UpdateMessageProducer producer;

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  public MockedStatic<Recipient> recipientMockedStatic;

  @Mock
  public MockedStatic<RecipientId> recipientIdMockedStatic;

  @Before
  public void setup() {
    you   = UUID.randomUUID();
    alice = UUID.randomUUID();
    bob   = UUID.randomUUID();

    recipientIdMockedStatic.when(() -> RecipientId.from(anyLong())).thenCallRealMethod();

    RecipientId aliceId = RecipientId.from(1);
    RecipientId bobId   = RecipientId.from(2);

    Recipient aliceRecipient = recipientWithName(aliceId, "Alice");
    Recipient bobRecipient   = recipientWithName(bobId, "Bob");

    producer = new GroupsV2UpdateMessageProducer(ApplicationProvider.getApplicationContext(), new ServiceIds(ACI.from(you), PNI.from(UUID.randomUUID())), null);

    recipientIdMockedStatic.when(() -> RecipientId.from(ACI.from(alice))).thenReturn(aliceId);
    recipientIdMockedStatic.when(() -> RecipientId.from(ACI.from(bob))).thenReturn(bobId);
    recipientMockedStatic.when(() -> Recipient.resolved(aliceId)).thenReturn(aliceRecipient);
    recipientMockedStatic.when(() -> Recipient.resolved(bobId)).thenReturn(bobRecipient);
  }

  private static Recipient recipientWithName(RecipientId id, String name) {
    Recipient recipient = mock(Recipient.class);
    when(recipient.getId()).thenReturn(id);
    when(recipient.getDisplayName(any())).thenReturn(name);
    return recipient;
  }

  @Test
  public void empty_change() {
    DecryptedGroupChange change = changeBy(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice updated the group.")));
  }

  @Test
  public void empty_change_by_you() {
    DecryptedGroupChange change = changeBy(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You updated the group.")));
  }

  @Test
  public void empty_change_by_unknown() {
    DecryptedGroupChange change = changeByUnknown()
                                    .build();

    assertThat(describeChange(change), is(singletonList("The group was updated.")));
  }

  // Member additions

  @Test
  public void member_added_member() {
    DecryptedGroupChange change = changeBy(alice)
                                    .addMember(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice added Bob.")));
  }

  @Test
  public void member_added_member_mentions_both() {
    DecryptedGroupChange change = changeBy(alice)
                                    .addMember(bob)
                                    .build();

    assertSingleChangeMentioning(change, Arrays.asList(alice, bob));
  }

  @Test
  public void you_added_member() {
    DecryptedGroupChange change = changeBy(you)
                                    .addMember(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You added Bob.")));
  }

  @Test
  public void you_added_member_mentions_just_member() {
    DecryptedGroupChange change = changeBy(you)
                                    .addMember(bob)
                                    .build();

    assertSingleChangeMentioning(change, singletonList(bob));
  }

  @Test
  public void member_added_you() {
    DecryptedGroupChange change = changeBy(alice)
                                    .addMember(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice added you to the group.")));
  }

  @Test
  public void you_added_you() {
    DecryptedGroupChange change = changeBy(you)
                                    .addMember(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You joined the group via the group link.")));
  }

  @Test
  public void member_added_themselves() {
    DecryptedGroupChange change = changeBy(bob)
                                    .addMember(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob joined the group via the group link.")));
  }

  @Test
  public void member_added_themselves_mentions_just_member() {
    DecryptedGroupChange change = changeBy(bob)
                                    .addMember(bob)
                                    .build();

    assertSingleChangeMentioning(change, singletonList(bob));
  }

  @Test
  public void unknown_added_you() {
    DecryptedGroupChange change = changeByUnknown()
                                    .addMember(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You joined the group.")));
  }

  @Test
  public void unknown_added_member() {
    DecryptedGroupChange change = changeByUnknown()
                                    .addMember(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob joined the group.")));
  }

  @Test
  public void member_added_you_and_another_where_you_are_not_first() {
    DecryptedGroupChange change = changeBy(bob)
                                    .addMember(alice)
                                    .addMember(you)
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList("Bob added you to the group.", "Bob added Alice.")));
  }

  @Test
  public void unknown_member_added_you_and_another_where_you_are_not_first() {
    DecryptedGroupChange change = changeByUnknown()
                                    .addMember(alice)
                                    .addMember(you)
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList("You joined the group.", "Alice joined the group.")));
  }

  @Test
  public void you_added_you_and_another_where_you_are_not_first() {
    DecryptedGroupChange change = changeBy(you)
                                    .addMember(alice)
                                    .addMember(you)
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList("You joined the group via the group link.", "You added Alice.")));
  }

  // Member removals
  @Test
  public void member_removed_member() {
    DecryptedGroupChange change = changeBy(alice)
                                    .deleteMember(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice removed Bob.")));
  }

  @Test
  public void you_removed_member() {
    DecryptedGroupChange change = changeBy(you)
                                    .deleteMember(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You removed Bob.")));
  }

  @Test
  public void member_removed_you() {
    DecryptedGroupChange change = changeBy(alice)
                                    .deleteMember(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice removed you from the group.")));
  }

  @Test
  public void you_removed_you() {
    DecryptedGroupChange change = changeBy(you)
                                    .deleteMember(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You left the group.")));
  }

  @Test
  public void member_removed_themselves() {
    DecryptedGroupChange change = changeBy(bob)
                                    .deleteMember(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob left the group.")));
  }

  @Test
  public void unknown_removed_member() {
    DecryptedGroupChange change = changeByUnknown()
                                    .deleteMember(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice is no longer in the group.")));
  }

  @Test
  public void unknown_removed_you() {
    DecryptedGroupChange change = changeByUnknown()
                                    .deleteMember(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You are no longer in the group.")));
  }

  // Member role modifications

  @Test
  public void you_make_member_admin() {
    DecryptedGroupChange change = changeBy(you)
                                    .promoteToAdmin(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You made Alice an admin.")));
  }

  @Test
  public void member_makes_member_admin() {
    DecryptedGroupChange change = changeBy(bob)
                                    .promoteToAdmin(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob made Alice an admin.")));
  }

  @Test
  public void member_makes_you_admin() {
    DecryptedGroupChange change = changeBy(alice)
                                    .promoteToAdmin(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice made you an admin.")));
  }

  @Test
  public void you_revoked_member_admin() {
    DecryptedGroupChange change = changeBy(you)
                                    .demoteToMember(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You revoked admin privileges from Bob.")));
  }

  @Test
  public void member_revokes_member_admin() {
    DecryptedGroupChange change = changeBy(bob)
                                    .demoteToMember(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob revoked admin privileges from Alice.")));
  }

  @Test
  public void member_revokes_your_admin() {
    DecryptedGroupChange change = changeBy(alice)
                                    .demoteToMember(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice revoked your admin privileges.")));
  }

  @Test
  public void unknown_makes_member_admin() {
    DecryptedGroupChange change = changeByUnknown()
                                    .promoteToAdmin(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice is now an admin.")));
  }

  @Test
  public void unknown_makes_you_admin() {
    DecryptedGroupChange change = changeByUnknown()
                                    .promoteToAdmin(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You are now an admin.")));
  }

  @Test
  public void unknown_revokes_member_admin() {
    DecryptedGroupChange change = changeByUnknown()
                                    .demoteToMember(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice is no longer an admin.")));
  }

  @Test
  public void unknown_revokes_your_admin() {
    DecryptedGroupChange change = changeByUnknown()
                                    .demoteToMember(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You are no longer an admin.")));
  }

  // Member invitation

  @Test
  public void you_invited_member() {
    DecryptedGroupChange change = changeBy(you)
                                    .invite(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You invited Alice to the group.")));
  }

  @Test
  public void member_invited_you() {
    DecryptedGroupChange change = changeBy(alice)
                                    .invite(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice invited you to the group.")));
  }

  @Test
  public void member_invited_1_person() {
    DecryptedGroupChange change = changeBy(alice)
                                    .invite(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice invited 1 person to the group.")));
  }

  @Test
  public void member_invited_2_persons() {
    DecryptedGroupChange change = changeBy(alice)
                                    .invite(bob)
                                    .invite(UUID.randomUUID())
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice invited 2 people to the group.")));
  }

  @Test
  public void member_invited_3_persons_and_you() {
    DecryptedGroupChange change = changeBy(bob)
                                    .invite(alice)
                                    .invite(you)
                                    .invite(UUID.randomUUID())
                                    .invite(UUID.randomUUID())
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList("Bob invited you to the group.", "Bob invited 3 people to the group.")));
  }

  @Test
  public void unknown_editor_but_known_invitee_invited_you() {
    DecryptedGroupChange change = changeByUnknown()
                                    .inviteBy(you, alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice invited you to the group.")));
  }

  @Test
  public void unknown_editor_and_unknown_inviter_invited_you() {
    DecryptedGroupChange change = changeByUnknown()
                                    .invite(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You were invited to the group.")));
  }

  @Test
  public void unknown_invited_1_person() {
    DecryptedGroupChange change = changeByUnknown()
                                    .invite(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("1 person was invited to the group.")));
  }

  @Test
  public void unknown_invited_2_persons() {
    DecryptedGroupChange change = changeByUnknown()
                                    .invite(alice)
                                    .invite(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("2 people were invited to the group.")));
  }

  @Test
  public void unknown_invited_3_persons_and_you() {
    DecryptedGroupChange change = changeByUnknown()
                                    .invite(alice)
                                    .invite(you)
                                    .invite(UUID.randomUUID())
                                    .invite(UUID.randomUUID())
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList("You were invited to the group.", "3 people were invited to the group.")));
  }

  @Test
  public void unknown_editor_invited_3_persons_and_you_inviter_known() {
    DecryptedGroupChange change = changeByUnknown()
                                    .invite(alice)
                                    .inviteBy(you, bob)
                                    .invite(UUID.randomUUID())
                                    .invite(UUID.randomUUID())
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList("Bob invited you to the group.", "3 people were invited to the group.")));
  }

  @Test
  public void member_invited_3_persons_and_you_and_added_another_where_you_were_not_first() {
    DecryptedGroupChange change = changeBy(bob)
                                    .addMember(alice)
                                    .invite(you)
                                    .invite(UUID.randomUUID())
                                    .invite(UUID.randomUUID())
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList("Bob invited you to the group.", "Bob added Alice.", "Bob invited 2 people to the group.")));
  }

  @Test
  public void unknown_editor_but_known_invitee_invited_you_and_added_another_where_you_were_not_first() {
    DecryptedGroupChange change = changeByUnknown()
                                    .addMember(bob)
                                    .inviteBy(you, alice)
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList("Alice invited you to the group.", "Bob joined the group.")));
  }

  @Test
  public void unknown_editor_and_unknown_inviter_invited_you_and_added_another_where_you_were_not_first() {
    DecryptedGroupChange change = changeByUnknown()
                                    .addMember(alice)
                                    .invite(you)
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList("You were invited to the group.", "Alice joined the group.")));
  }

  // Member invitation revocation

  @Test
  public void member_uninvited_1_person() {
    DecryptedGroupChange change = changeBy(alice)
                                    .uninvite(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice revoked an invitation to the group.")));
  }

  @Test
  public void member_uninvited_2_people() {
    DecryptedGroupChange change = changeBy(alice)
                                    .uninvite(bob)
                                    .uninvite(UUID.randomUUID())
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice revoked 2 invitations to the group.")));
  }

  @Test
  public void you_uninvited_1_person() {
    DecryptedGroupChange change = changeBy(you)
                                    .uninvite(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You revoked an invitation to the group.")));
  }

  @Test
  public void you_uninvited_2_people() {
    DecryptedGroupChange change = changeBy(you)
                                    .uninvite(bob)
                                    .uninvite(UUID.randomUUID())
                                    .build();

    assertThat(describeChange(change), is(singletonList("You revoked 2 invitations to the group.")));
  }

  @Test
  public void pending_member_declines_invite() {
    DecryptedGroupChange change = changeBy(bob)
                                    .uninvite(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Someone declined an invitation to the group.")));
  }

  @Test
  public void you_decline_invite() {
    DecryptedGroupChange change = changeBy(you)
                                    .uninvite(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You declined the invitation to the group.")));
  }

  @Test
  public void unknown_revokes_your_invite() {
    DecryptedGroupChange change = changeByUnknown()
                                    .uninvite(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("An admin revoked your invitation to the group.")));
  }

  @Test
  public void unknown_revokes_1_invite() {
    DecryptedGroupChange change = changeByUnknown()
                                    .uninvite(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("An invitation to the group was revoked.")));
  }

  @Test
  public void unknown_revokes_2_invites() {
    DecryptedGroupChange change = changeByUnknown()
                                    .uninvite(bob)
                                    .uninvite(UUID.randomUUID())
                                    .build();

    assertThat(describeChange(change), is(singletonList("2 invitations to the group were revoked.")));
  }

  @Test
  public void unknown_revokes_yours_and_three_other_invites() {
    DecryptedGroupChange change = changeByUnknown()
                                    .uninvite(bob)
                                    .uninvite(you)
                                    .uninvite(UUID.randomUUID())
                                    .uninvite(UUID.randomUUID())
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList("An admin revoked your invitation to the group.", "3 invitations to the group were revoked.")));
  }

  @Test
  public void your_invite_was_revoked_by_known_member() {
    DecryptedGroupChange change = changeBy(bob)
                                    .uninvite(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob revoked your invitation to the group.")));
  }

  // Promote pending members

  @Test
  public void member_accepts_invite() {
    DecryptedGroupChange change = changeBy(bob)
                                    .promote(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob accepted an invitation to the group.")));
  }

  @Test
  public void you_accept_invite() {
    DecryptedGroupChange change = changeBy(you)
                                    .promote(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You accepted the invitation to the group.")));
  }

  @Test
  public void member_promotes_pending_member() {
    DecryptedGroupChange change = changeBy(bob)
                                    .promote(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob added invited member Alice.")));
  }

  @Test
  public void you_promote_pending_member() {
    DecryptedGroupChange change = changeBy(you)
                                    .promote(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You added invited member Bob.")));
  }

  @Test
  public void member_promotes_you() {
    DecryptedGroupChange change = changeBy(bob)
                                    .promote(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob added you to the group.")));
  }

  @Test
  public void unknown_added_by_invite() {
    DecryptedGroupChange change = changeByUnknown()
                                    .promote(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You joined the group.")));
  }

  @Test
  public void unknown_promotes_pending_member() {
    DecryptedGroupChange change = changeByUnknown()
                                    .promote(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice joined the group.")));
  }

  // Title change

  @Test
  public void member_changes_title() {
    DecryptedGroupChange change = changeBy(alice)
                                    .title("New title")
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice changed the group name to \"" + isolateBidi("New title") + "\".")));
  }

  @Test
  public void you_change_title() {
    DecryptedGroupChange change = changeBy(you)
                                    .title("Title 2")
                                    .build();

    assertThat(describeChange(change), is(singletonList("You changed the group name to \"" + isolateBidi("Title 2") + "\".")));
  }

  @Test
  public void unknown_changed_title() {
    DecryptedGroupChange change = changeByUnknown()
                                    .title("Title 3")
                                    .build();

    assertThat(describeChange(change), is(singletonList("The group name has changed to \"" + isolateBidi("Title 3") + "\".")));
  }
  
  // Avatar change

  @Test
  public void member_changes_avatar() {
    DecryptedGroupChange change = changeBy(alice)
                                    .avatar("Avatar1")
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice changed the group avatar.")));
  }

  @Test
  public void you_change_avatar() {
    DecryptedGroupChange change = changeBy(you)
                                    .avatar("Avatar2")
                                    .build();

    assertThat(describeChange(change), is(singletonList("You changed the group avatar.")));
  }

  @Test
  public void unknown_changed_avatar() {
    DecryptedGroupChange change = changeByUnknown()
                                    .avatar("Avatar3")
                                    .build();

    assertThat(describeChange(change), is(singletonList("The group avatar has been changed.")));
  }

  // Timer change

  @Test
  public void member_changes_timer() {
    DecryptedGroupChange change = changeBy(bob)
                                    .timer(10)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob set the disappearing message timer to 10 seconds.")));
  }

  @Test
  public void you_change_timer() {
    DecryptedGroupChange change = changeBy(you)
                                    .timer(60)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You set the disappearing message timer to 1 minute.")));
  }

  @Test
  public void unknown_change_timer() {
    DecryptedGroupChange change = changeByUnknown()
                                    .timer(120)
                                    .build();

    assertThat(describeChange(change), is(singletonList("The disappearing message timer has been set to 2 minutes.")));
  }

  @Test
  public void unknown_change_timer_mentions_no_one() {
    DecryptedGroupChange change = changeByUnknown()
                                    .timer(120)
                                    .build();

    assertSingleChangeMentioning(change, emptyList());
  }

  // Attribute access change

  @Test
  public void member_changes_attribute_access() {
    DecryptedGroupChange change = changeBy(bob)
                                    .attributeAccess(AccessControl.AccessRequired.MEMBER)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob changed who can edit group info to \"All members\".")));
  }

  @Test
  public void you_changed_attribute_access() {
    DecryptedGroupChange change = changeBy(you)
                                    .attributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You changed who can edit group info to \"Only admins\".")));
  }

  @Test
  public void unknown_changed_attribute_access() {
    DecryptedGroupChange change = changeByUnknown()
                                    .attributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Who can edit group info has been changed to \"Only admins\".")));
  }

  // Membership access change

  @Test
  public void member_changes_membership_access() {
    DecryptedGroupChange change = changeBy(alice)
                                    .membershipAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice changed who can edit group membership to \"Only admins\".")));
  }

  @Test
  public void you_changed_membership_access() {
    DecryptedGroupChange change = changeBy(you)
                                    .membershipAccess(AccessControl.AccessRequired.MEMBER)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You changed who can edit group membership to \"All members\".")));
  }

  @Test
  public void unknown_changed_membership_access() {
    DecryptedGroupChange change = changeByUnknown()
                                    .membershipAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Who can edit group membership has been changed to \"Only admins\".")));
  }

  // Group link access change

  @Test
  public void you_changed_group_link_access_to_any() {
    DecryptedGroupChange change = changeBy(you)
                                    .inviteLinkAccess(AccessControl.AccessRequired.ANY)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You turned on the group link with admin approval off.")));
  }

  @Test
  public void you_changed_group_link_access_to_administrator_approval() {
    DecryptedGroupChange change = changeBy(you)
                                    .inviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You turned on the group link with admin approval on.")));
  }

  @Test
  public void you_turned_off_group_link_access() {
    DecryptedGroupChange change = changeBy(you)
                                    .inviteLinkAccess(AccessControl.AccessRequired.UNSATISFIABLE)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You turned off the group link.")));
  }

  @Test
  public void member_changed_group_link_access_to_any() {
    DecryptedGroupChange change = changeBy(alice)
                                    .inviteLinkAccess(AccessControl.AccessRequired.ANY)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice turned on the group link with admin approval off.")));
  }

  @Test
  public void member_changed_group_link_access_to_administrator_approval() {
    DecryptedGroupChange change = changeBy(bob)
                                    .inviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob turned on the group link with admin approval on.")));
  }

  @Test
  public void member_turned_off_group_link_access() {
    DecryptedGroupChange change = changeBy(alice)
                                    .inviteLinkAccess(AccessControl.AccessRequired.UNSATISFIABLE)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice turned off the group link.")));
  }

  @Test
  public void unknown_changed_group_link_access_to_any() {
    DecryptedGroupChange change = changeByUnknown()
                                    .inviteLinkAccess(AccessControl.AccessRequired.ANY)
                                    .build();

    assertThat(describeChange(change), is(singletonList("The group link has been turned on with admin approval off.")));
  }

  @Test
  public void unknown_changed_group_link_access_to_administrator_approval() {
    DecryptedGroupChange change = changeByUnknown()
                                    .inviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .build();

    assertThat(describeChange(change), is(singletonList("The group link has been turned on with admin approval on.")));
  }

  @Test
  public void unknown_turned_off_group_link_access() {
    DecryptedGroupChange change = changeByUnknown()
                                    .inviteLinkAccess(AccessControl.AccessRequired.UNSATISFIABLE)
                                    .build();

    assertThat(describeChange(change), is(singletonList("The group link has been turned off.")));
  }

  // Group link with known previous group state

  @Test
  public void group_link_access_from_unknown_to_administrator() {
    assertEquals("You turned on the group link with admin approval on.", describeGroupLinkChange(you, AccessControl.AccessRequired.UNKNOWN, AccessControl.AccessRequired.ADMINISTRATOR));
    assertEquals("Alice turned on the group link with admin approval on.", describeGroupLinkChange(alice, AccessControl.AccessRequired.UNKNOWN, AccessControl.AccessRequired.ADMINISTRATOR));
    assertEquals("The group link has been turned on with admin approval on.", describeGroupLinkChange(null, AccessControl.AccessRequired.UNKNOWN, AccessControl.AccessRequired.ADMINISTRATOR));
  }

  @Test
  public void group_link_access_from_administrator_to_unsatisfiable() {
    assertEquals("You turned off the group link.", describeGroupLinkChange(you, AccessControl.AccessRequired.ADMINISTRATOR, AccessControl.AccessRequired.UNSATISFIABLE));
    assertEquals("Bob turned off the group link.", describeGroupLinkChange(bob, AccessControl.AccessRequired.ADMINISTRATOR, AccessControl.AccessRequired.UNSATISFIABLE));
    assertEquals("The group link has been turned off.", describeGroupLinkChange(null, AccessControl.AccessRequired.ADMINISTRATOR, AccessControl.AccessRequired.UNSATISFIABLE));
  }

  @Test
  public void group_link_access_from_unsatisfiable_to_administrator() {
    assertEquals("You turned on the group link with admin approval on.", describeGroupLinkChange(you, AccessControl.AccessRequired.UNSATISFIABLE, AccessControl.AccessRequired.ADMINISTRATOR));
    assertEquals("Alice turned on the group link with admin approval on.", describeGroupLinkChange(alice, AccessControl.AccessRequired.UNSATISFIABLE, AccessControl.AccessRequired.ADMINISTRATOR));
    assertEquals("The group link has been turned on with admin approval on.", describeGroupLinkChange(null, AccessControl.AccessRequired.UNSATISFIABLE, AccessControl.AccessRequired.ADMINISTRATOR));
  }

  @Test
  public void group_link_access_from_administrator_to_any() {
    assertEquals("You turned off admin approval for the group link.", describeGroupLinkChange(you, AccessControl.AccessRequired.ADMINISTRATOR, AccessControl.AccessRequired.ANY));
    assertEquals("Bob turned off admin approval for the group link.", describeGroupLinkChange(bob, AccessControl.AccessRequired.ADMINISTRATOR, AccessControl.AccessRequired.ANY));
    assertEquals("The admin approval for the group link has been turned off.", describeGroupLinkChange(null, AccessControl.AccessRequired.ADMINISTRATOR, AccessControl.AccessRequired.ANY));
  }

  @Test
  public void group_link_access_from_any_to_administrator() {
    assertEquals("You turned on admin approval for the group link.", describeGroupLinkChange(you, AccessControl.AccessRequired.ANY, AccessControl.AccessRequired.ADMINISTRATOR));
    assertEquals("Bob turned on admin approval for the group link.", describeGroupLinkChange(bob, AccessControl.AccessRequired.ANY, AccessControl.AccessRequired.ADMINISTRATOR));
    assertEquals("The admin approval for the group link has been turned on.", describeGroupLinkChange(null, AccessControl.AccessRequired.ANY, AccessControl.AccessRequired.ADMINISTRATOR));
  }

  private String describeGroupLinkChange(@Nullable UUID editor, @NonNull AccessControl.AccessRequired fromAccess, AccessControl.AccessRequired toAccess){
    DecryptedGroup       previousGroupState = DecryptedGroup.newBuilder()
                                                            .setAccessControl(AccessControl.newBuilder()
                                                                                           .setAddFromInviteLink(fromAccess))
                                                            .build();
    DecryptedGroupChange change             = (editor != null ? changeBy(editor) : changeByUnknown()).inviteLinkAccess(toAccess)
                                                                                                     .build();

    List<String> strings = describeChange(previousGroupState, change);
    assertEquals(1, strings.size());
    return strings.get(0);
  }

  // Group link reset

  @Test
  public void you_reset_group_link() {
    DecryptedGroupChange change = changeBy(you)
                                    .resetGroupLink()
                                    .build();

    assertThat(describeChange(change), is(singletonList("You reset the group link.")));
  }

  @Test
  public void member_reset_group_link() {
    DecryptedGroupChange change = changeBy(alice)
                                    .resetGroupLink()
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice reset the group link.")));
  }

  @Test
  public void unknown_reset_group_link() {
    DecryptedGroupChange change = changeByUnknown()
                                    .resetGroupLink()
                                    .build();

    assertThat(describeChange(change), is(singletonList("The group link has been reset.")));
  }

  /**
   * When the group link is turned on and reset in the same change, assume this is the first time
   * the link password it being set and do not show reset message.
   */
  @Test
  public void member_changed_group_link_access_to_on_and_reset() {
    DecryptedGroupChange change = changeBy(alice)
                                    .inviteLinkAccess(AccessControl.AccessRequired.ANY)
                                    .resetGroupLink()
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice turned on the group link with admin approval off.")));
  }

  /**
   * When the group link is turned on and reset in the same change, assume this is the first time
   * the link password it being set and do not show reset message.
   */
  @Test
  public void you_changed_group_link_access_to_on_and_reset() {
    DecryptedGroupChange change = changeBy(you)
                                    .inviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                    .resetGroupLink()
                                    .build();

    assertThat(describeChange(change), is(singletonList("You turned on the group link with admin approval on.")));
  }

  @Test
  public void you_changed_group_link_access_to_off_and_reset() {
    DecryptedGroupChange change = changeBy(you)
                                    .inviteLinkAccess(AccessControl.AccessRequired.UNSATISFIABLE)
                                    .resetGroupLink()
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList("You turned off the group link.", "You reset the group link.")));
  }

  // Group link request

  @Test
  public void you_requested_to_join_the_group() {
    DecryptedGroupChange change = changeBy(you)
                                    .requestJoin()
                                    .build();

    assertThat(describeChange(change), is(singletonList("You sent a request to join the group.")));
  }

  @Test
  public void member_requested_to_join_the_group() {
    DecryptedGroupChange change = changeBy(bob)
                                    .requestJoin()
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob requested to join via the group link.")));
  }

  @Test
  public void unknown_requested_to_join_the_group() {
    DecryptedGroupChange change = changeByUnknown()
                                    .requestJoin(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice requested to join via the group link.")));
  }

  @Test
  public void member_approved_your_join_request() {
    DecryptedGroupChange change = changeBy(bob)
                                    .approveRequest(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Bob approved your request to join the group.")));
  }

  @Test
  public void member_approved_another_join_request() {
    DecryptedGroupChange change = changeBy(alice)
                                    .approveRequest(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice approved a request to join the group from Bob.")));
  }

  @Test
  public void you_approved_another_join_request() {
    DecryptedGroupChange change = changeBy(you)
                                    .approveRequest(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You approved a request to join the group from Alice.")));
  }

  @Test
  public void unknown_approved_your_join_request() {
    DecryptedGroupChange change = changeByUnknown()
                                    .approveRequest(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Your request to join the group has been approved.")));
  }

  @Test
  public void unknown_approved_another_join_request() {
    DecryptedGroupChange change = changeByUnknown()
                                    .approveRequest(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("A request to join the group from Bob has been approved.")));
  }
  
  @Test
  public void member_denied_another_join_request() {
    DecryptedGroupChange change = changeBy(alice)
                                    .denyRequest(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice denied a request to join the group from Bob.")));
  }

  @Test
  public void member_denied_your_join_request() {
    DecryptedGroupChange change = changeBy(alice)
                                    .denyRequest(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Your request to join the group has been denied by an admin.")));
  }

  @Test
  public void you_cancelled_your_join_request() {
    DecryptedGroupChange change = changeBy(you)
                                    .denyRequest(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("You canceled your request to join the group.")));
  }

  @Test
  public void member_cancelled_their_join_request() {
    DecryptedGroupChange change = changeBy(alice)
                                    .denyRequest(alice)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Alice canceled their request to join the group.")));
  }

  @Test
  public void unknown_denied_your_join_request() {
    DecryptedGroupChange change = changeByUnknown()
                                    .denyRequest(you)
                                    .build();

    assertThat(describeChange(change), is(singletonList("Your request to join the group has been denied by an admin.")));
  }

  @Test
  public void unknown_denied_another_join_request() {
    DecryptedGroupChange change = changeByUnknown()
                                    .denyRequest(bob)
                                    .build();

    assertThat(describeChange(change), is(singletonList("A request to join the group from Bob has been denied.")));
  }

  // Multiple changes

  @Test
  public void multiple_changes() {
    DecryptedGroupChange change = changeBy(alice)
                                    .addMember(bob)
                                    .membershipAccess(AccessControl.AccessRequired.MEMBER)
                                    .title("Title")
                                    .addMember(you)
                                    .timer(300)
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList(
      "Alice added you to the group.",
      "Alice added Bob.",
      "Alice changed the group name to \"" + isolateBidi("Title") + "\".",
      "Alice set the disappearing message timer to 5 minutes.",
      "Alice changed who can edit group membership to \"All members\".")));
  }

  @Test
  public void multiple_changes_leave_and_promote() {
    DecryptedGroupChange change = changeBy(alice)
                                    .deleteMember(alice)
                                    .promoteToAdmin(bob)
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList(
      "Alice made Bob an admin.",
      "Alice left the group.")));
  }

  @Test
  public void multiple_changes_leave_and_promote_by_unknown() {
    DecryptedGroupChange change = changeByUnknown()
                                    .deleteMember(alice)
                                    .promoteToAdmin(bob)
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList(
      "Bob is now an admin.",
      "Alice is no longer in the group.")));
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

    assertThat(describeChange(change), is(Arrays.asList(
      "Bob joined the group.",
      "The group name has changed to \"" + isolateBidi("Title 2") + "\".",
      "The group avatar has been changed.",
      "The disappearing message timer has been set to 10 minutes.",
      "Who can edit group membership has been changed to \"All members\".")));
  }

  @Test
  public void multiple_changes_join_and_leave_by_unknown() {
    DecryptedGroupChange change = changeByUnknown()
                                    .addMember(alice)
                                    .promoteToAdmin(alice)
                                    .deleteMember(alice)
                                    .title("Updated title")
                                    .build();

    assertThat(describeChange(change), is(Arrays.asList(
      "Alice joined the group.",
      "Alice is now an admin.",
      "The group name has changed to \"" + isolateBidi("Updated title") + "\".",
      "Alice is no longer in the group.")));
  }

  // Group state without a change record

  @Test
  public void you_created_a_group_change_not_found() {
    DecryptedGroup group = newGroupBy(you, 0)
                             .build();

    assertThat(describeNewGroup(group), is("You joined the group."));
  }

  @Test
  public void you_created_a_group() {
    DecryptedGroup group = newGroupBy(you, 0)
                             .build();

    DecryptedGroupChange change = changeBy(you)
                                    .addMember(alice)
                                    .addMember(you)
                                    .addMember(bob)
                                    .title("New title")
                                    .build();

    assertThat(describeNewGroup(group, change), is("You created the group."));
  }

  @Test
  public void alice_created_a_group_change_not_found() {
    DecryptedGroup group = newGroupBy(alice, 0)
                             .member(you)
                             .build();

    assertThat(describeNewGroup(group), is("You joined the group."));
  }

  @Test
  public void alice_created_a_group() {
    DecryptedGroup group = newGroupBy(alice, 0)
                             .member(you)
                             .build();

    DecryptedGroupChange change = changeBy(alice)
                                    .addMember(you)
                                    .addMember(alice)
                                    .addMember(bob)
                                    .title("New title")
                                    .build();

    assertThat(describeNewGroup(group, change), is("Alice added you to the group."));
  }

  @Test
  public void alice_created_a_group_above_zero() {
    DecryptedGroup group = newGroupBy(alice, 1)
                             .member(you)
                             .build();

    assertThat(describeNewGroup(group), is("You joined the group."));
  }

  @Test
  public void you_were_invited_to_a_group() {
    DecryptedGroup group = newGroupBy(alice, 0)
                             .invite(bob, you)
                             .build();

    assertThat(describeNewGroup(group), is("Bob invited you to the group."));
  }

  @Test
  public void describe_a_group_you_are_not_in() {
    DecryptedGroup group = newGroupBy(alice, 1)
                             .build();

    assertThat(describeNewGroup(group), is("Group updated."));
  }

  @Test
  public void makeRecipientsClickable_onePlaceholder() {
    RecipientId id = RecipientId.from(1);

    Spannable result = GroupsV2UpdateMessageProducer.makeRecipientsClickable(
        ApplicationProvider.getApplicationContext(),
        GroupsV2UpdateMessageProducer.makePlaceholder(id),
        Collections.singletonList(id),
        null
    );

    assertEquals("Alice", result.toString());
  }

  @Test
  public void makeRecipientsClickable_twoPlaceholders_sameRecipient() {
    RecipientId id          = RecipientId.from(1);
    String      placeholder = GroupsV2UpdateMessageProducer.makePlaceholder(id);

    Spannable result = GroupsV2UpdateMessageProducer.makeRecipientsClickable(
        ApplicationProvider.getApplicationContext(),
        placeholder + " " + placeholder,
        Collections.singletonList(id),
        null
    );

    assertEquals("Alice Alice", result.toString());
  }

  @Test
  public void makeRecipientsClickable_twoPlaceholders_differentRecipient() {
    RecipientId id1 = RecipientId.from(1);
    RecipientId id2 = RecipientId.from(2);

    String placeholder1 = GroupsV2UpdateMessageProducer.makePlaceholder(id1);
    String placeholder2 = GroupsV2UpdateMessageProducer.makePlaceholder(id2);

    Spannable result = GroupsV2UpdateMessageProducer.makeRecipientsClickable(
        ApplicationProvider.getApplicationContext(),
        placeholder1 + " " + placeholder2,
        Arrays.asList(id1, id2),
        null
    );

    assertEquals("Alice Bob", result.toString());
  }

  @Test
  public void makeRecipientsClickable_complicated() {
    RecipientId id1 = RecipientId.from(1);
    RecipientId id2 = RecipientId.from(2);

    String placeholder1 = GroupsV2UpdateMessageProducer.makePlaceholder(id1);
    String placeholder2 = GroupsV2UpdateMessageProducer.makePlaceholder(id2);

    Spannable result = GroupsV2UpdateMessageProducer.makeRecipientsClickable(
        ApplicationProvider.getApplicationContext(),
        placeholder1 + " said hello to " + placeholder2 + ", and " + placeholder2 + " said hello back to " + placeholder1 + ".",
        Arrays.asList(id1, id2),
        null
    );

    assertEquals("Alice said hello to Bob, and Bob said hello back to Alice.", result.toString());
  }

  private @NonNull List<String> describeChange(@NonNull DecryptedGroupChange change) {
    return describeChange(null, change);
  }

  private @NonNull List<String> describeChange(@Nullable DecryptedGroup previousGroupState,
                                                  @NonNull DecryptedGroupChange change)
  {
    return Stream.of(producer.describeChanges(previousGroupState, change))
                 .map(UpdateDescription::getSpannable)
                 .map(Spannable::toString)
                 .toList();
  }

  private @NonNull String describeNewGroup(@NonNull DecryptedGroup group) {
    return describeNewGroup(group, DecryptedGroupChange.getDefaultInstance());
  }

  private @NonNull String describeNewGroup(@NonNull DecryptedGroup group, @NonNull DecryptedGroupChange groupChange) {
    return producer.describeNewGroup(group, groupChange).getSpannable().toString();
  }

  private static GroupStateBuilder newGroupBy(UUID foundingMember, int revision) {
    return new GroupStateBuilder(foundingMember, revision);
  }

  private void assertSingleChangeMentioning(DecryptedGroupChange change, List<UUID> expectedMentions) {
    List<ServiceId> expectedMentionSids = expectedMentions.stream().map(ACI::from).collect(Collectors.toList());

    List<UpdateDescription> changes = producer.describeChanges(null, change);

    assertThat(changes.size(), is(1));

    UpdateDescription description = changes.get(0);
    assertThat(description.getMentioned(), is(expectedMentionSids));

    if (expectedMentions.isEmpty()) {
      assertTrue(description.isStringStatic());
    } else {
      assertFalse(description.isStringStatic());
    }
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
                                                       .setServiceIdBinary(UuidUtil.toByteString(invitee))
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
}
