package org.thoughtcrime.securesms.database.model

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.BidiUtil
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.AccessControl.AccessRequired
import org.signal.storageservice.protos.groups.AccessControl.AccessRequired.ADMINISTRATOR
import org.signal.storageservice.protos.groups.AccessControl.AccessRequired.ANY
import org.signal.storageservice.protos.groups.AccessControl.AccessRequired.MEMBER
import org.signal.storageservice.protos.groups.AccessControl.AccessRequired.UNKNOWN
import org.signal.storageservice.protos.groups.AccessControl.AccessRequired.UNSATISFIABLE
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.thoughtcrime.securesms.database.model.GroupsV2UpdateMessageConverter.translateDecryptedChangeNewGroup
import org.thoughtcrime.securesms.database.model.GroupsV2UpdateMessageConverter.translateDecryptedChangeUpdate
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.groups.v2.ChangeBuilder
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.Companion.resolved
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.push.ServiceIds
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroupsV2UpdateMessageProducerTest {
  private val you = ACI.from(UUID.randomUUID())
  private val alice = ACI.from(UUID.randomUUID())
  private val bob = ACI.from(UUID.randomUUID())
  private val selfIds = ServiceIds(you, PNI.from(UUID.randomUUID()))
  private val producer = GroupsV2UpdateMessageProducer(ApplicationProvider.getApplicationContext(), selfIds, null)

  @Before
  fun setup() {
    mockkStatic(RecipientId::class)
    val aliceId = RecipientId.from(1)
    val bobId = RecipientId.from(2)
    every { RecipientId.from(alice) } returns aliceId
    every { RecipientId.from(bob) } returns bobId

    mockkObject(Recipient.Companion)
    val aliceRecipient = recipientWithName(aliceId, "Alice")
    val bobRecipient = recipientWithName(bobId, "Bob")
    every { resolved(aliceId) } returns aliceRecipient
    every { resolved(bobId) } returns bobRecipient
  }

  @Test
  fun empty_change() {
    val change = ChangeBuilder.changeBy(alice)
      .build()

    assertEquals(listOf("Alice updated the group."), describeChange(change))
  }

  @Test
  fun empty_change_by_you() {
    val change = ChangeBuilder.changeBy(you)
      .build()

    assertEquals(listOf("You updated the group."), describeChange(change))
  }

  @Test
  fun empty_change_by_unknown() {
    val change = ChangeBuilder.changeByUnknown()
      .build()

    assertEquals(listOf("The group was updated."), describeChange(change))
  }

  // Member additions
  @Test
  fun member_added_member() {
    val change = ChangeBuilder.changeBy(alice)
      .addMember(bob)
      .build()

    assertEquals(listOf("Alice added Bob."), describeChange(change))
  }

  @Test
  fun member_added_member_mentions_both() {
    val change = ChangeBuilder.changeBy(alice)
      .addMember(bob)
      .build()

    assertSingleChangeMentioning(change, listOf(alice, bob))
  }

  @Test
  fun you_added_member() {
    val change = ChangeBuilder.changeBy(you)
      .addMember(bob)
      .build()

    assertEquals(listOf("You added Bob."), describeChange(change))
  }

  @Test
  fun you_added_member_mentions_just_member() {
    val change = ChangeBuilder.changeBy(you)
      .addMember(bob)
      .build()

    assertSingleChangeMentioning(change, listOf(bob))
  }

  @Test
  fun member_added_you() {
    val change = ChangeBuilder.changeBy(alice)
      .addMember(you)
      .build()

    assertEquals(listOf("Alice added you to the group."), describeChange(change))
  }

  @Test
  fun you_added_you() {
    val change = ChangeBuilder.changeBy(you)
      .addMember(you)
      .build()

    assertEquals(listOf("You joined the group via the group link."), describeChange(change))
  }

  @Test
  fun member_added_themselves() {
    val change = ChangeBuilder.changeBy(bob)
      .addMember(bob)
      .build()

    assertEquals(listOf("Bob joined the group via the group link."), describeChange(change))
  }

  @Test
  fun member_added_themselves_mentions_just_member() {
    val change = ChangeBuilder.changeBy(bob)
      .addMember(bob)
      .build()

    assertSingleChangeMentioning(change, listOf(bob))
  }

  @Test
  fun unknown_added_you() {
    val change = ChangeBuilder.changeByUnknown()
      .addMember(you)
      .build()

    assertEquals(listOf("You joined the group."), describeChange(change))
  }

  @Test
  fun unknown_added_member() {
    val change = ChangeBuilder.changeByUnknown()
      .addMember(bob)
      .build()

    assertEquals(listOf("Bob joined the group."), describeChange(change))
  }

  @Test
  fun member_added_you_and_another_where_you_are_not_first() {
    val change = ChangeBuilder.changeBy(bob)
      .addMember(alice)
      .addMember(you)
      .build()

    assertEquals(listOf("Bob added you to the group.", "Bob added Alice."), describeChange(change))
  }

  @Test
  fun unknown_member_added_you_and_another_where_you_are_not_first() {
    val change = ChangeBuilder.changeByUnknown()
      .addMember(alice)
      .addMember(you)
      .build()

    assertEquals(listOf("You joined the group.", "Alice joined the group."), describeChange(change))
  }

  @Test
  fun you_added_you_and_another_where_you_are_not_first() {
    val change = ChangeBuilder.changeBy(you)
      .addMember(alice)
      .addMember(you)
      .build()

    assertEquals(listOf("You joined the group via the group link.", "You added Alice."), describeChange(change))
  }

  // Member removals
  @Test
  fun member_removed_member() {
    val change = ChangeBuilder.changeBy(alice)
      .deleteMember(bob)
      .build()

    assertEquals(listOf("Alice removed Bob."), describeChange(change))
  }

  @Test
  fun you_removed_member() {
    val change = ChangeBuilder.changeBy(you)
      .deleteMember(bob)
      .build()

    assertEquals(listOf("You removed Bob."), describeChange(change))
  }

  @Test
  fun member_removed_you() {
    val change = ChangeBuilder.changeBy(alice)
      .deleteMember(you)
      .build()

    assertEquals(listOf("Alice removed you from the group."), describeChange(change))
  }

  @Test
  fun you_removed_you() {
    val change = ChangeBuilder.changeBy(you)
      .deleteMember(you)
      .build()

    assertEquals(listOf("You left the group."), describeChange(change))
  }

  @Test
  fun member_removed_themselves() {
    val change = ChangeBuilder.changeBy(bob)
      .deleteMember(bob)
      .build()

    assertEquals(listOf("Bob left the group."), describeChange(change))
  }

  @Test
  fun unknown_removed_member() {
    val change = ChangeBuilder.changeByUnknown()
      .deleteMember(alice)
      .build()

    assertEquals(listOf("Alice is no longer in the group."), describeChange(change))
  }

  @Test
  fun unknown_removed_you() {
    val change = ChangeBuilder.changeByUnknown()
      .deleteMember(you)
      .build()

    assertEquals(listOf("You are no longer in the group."), describeChange(change))
  }

  // Member role modifications
  @Test
  fun you_make_member_admin() {
    val change = ChangeBuilder.changeBy(you)
      .promoteToAdmin(alice)
      .build()

    assertEquals(listOf("You made Alice an admin."), describeChange(change))
  }

  @Test
  fun member_makes_member_admin() {
    val change = ChangeBuilder.changeBy(bob)
      .promoteToAdmin(alice)
      .build()

    assertEquals(listOf("Bob made Alice an admin."), describeChange(change))
  }

  @Test
  fun member_makes_you_admin() {
    val change = ChangeBuilder.changeBy(alice)
      .promoteToAdmin(you)
      .build()

    assertEquals(listOf("Alice made you an admin."), describeChange(change))
  }

  @Test
  fun you_revoked_member_admin() {
    val change = ChangeBuilder.changeBy(you)
      .demoteToMember(bob)
      .build()

    assertEquals(listOf("You revoked admin privileges from Bob."), describeChange(change))
  }

  @Test
  fun member_revokes_member_admin() {
    val change = ChangeBuilder.changeBy(bob)
      .demoteToMember(alice)
      .build()

    assertEquals(listOf("Bob revoked admin privileges from Alice."), describeChange(change))
  }

  @Test
  fun member_revokes_your_admin() {
    val change = ChangeBuilder.changeBy(alice)
      .demoteToMember(you)
      .build()

    assertEquals(listOf("Alice revoked your admin privileges."), describeChange(change))
  }

  @Test
  fun unknown_makes_member_admin() {
    val change = ChangeBuilder.changeByUnknown()
      .promoteToAdmin(alice)
      .build()

    assertEquals(listOf("Alice is now an admin."), describeChange(change))
  }

  @Test
  fun unknown_makes_you_admin() {
    val change = ChangeBuilder.changeByUnknown()
      .promoteToAdmin(you)
      .build()

    assertEquals(listOf("You are now an admin."), describeChange(change))
  }

  @Test
  fun unknown_revokes_member_admin() {
    val change = ChangeBuilder.changeByUnknown()
      .demoteToMember(alice)
      .build()

    assertEquals(listOf("Alice is no longer an admin."), describeChange(change))
  }

  @Test
  fun unknown_revokes_your_admin() {
    val change = ChangeBuilder.changeByUnknown()
      .demoteToMember(you)
      .build()

    assertEquals(listOf("You are no longer an admin."), describeChange(change))
  }

  // Member invitation
  @Test
  fun you_invited_member() {
    val change = ChangeBuilder.changeBy(you)
      .invite(alice)
      .build()

    assertEquals(listOf("You invited Alice to the group."), describeChange(change))
  }

  @Test
  fun member_invited_you() {
    val change = ChangeBuilder.changeBy(alice)
      .invite(you)
      .build()

    assertEquals(listOf("Alice invited you to the group."), describeChange(change))
  }

  @Test
  fun member_invited_1_person() {
    val change = ChangeBuilder.changeBy(alice)
      .invite(bob)
      .build()

    assertEquals(listOf("Alice invited 1 person to the group."), describeChange(change))
  }

  @Test
  fun member_invited_2_persons() {
    val change = ChangeBuilder.changeBy(alice)
      .invite(bob)
      .invite(ACI.from(UUID.randomUUID()))
      .build()

    assertEquals(listOf("Alice invited 2 people to the group."), describeChange(change))
  }

  @Test
  fun member_invited_3_persons_and_you() {
    val change = ChangeBuilder.changeBy(bob)
      .invite(alice)
      .invite(you)
      .invite(ACI.from(UUID.randomUUID()))
      .invite(ACI.from(UUID.randomUUID()))
      .build()

    assertEquals(listOf("Bob invited you to the group.", "Bob invited 3 people to the group."), describeChange(change))
  }

  @Test
  fun unknown_editor_but_known_invitee_invited_you() {
    val change = ChangeBuilder.changeByUnknown()
      .inviteBy(you, alice)
      .build()

    assertEquals(listOf("Alice invited you to the group."), describeChange(change))
  }

  @Test
  fun unknown_editor_and_unknown_inviter_invited_you() {
    val change = ChangeBuilder.changeByUnknown()
      .invite(you)
      .build()

    assertEquals(listOf("You were invited to the group."), describeChange(change))
  }

  @Test
  fun unknown_invited_1_person() {
    val change = ChangeBuilder.changeByUnknown()
      .invite(alice)
      .build()

    assertEquals(listOf("1 person was invited to the group."), describeChange(change))
  }

  @Test
  fun unknown_invited_2_persons() {
    val change = ChangeBuilder.changeByUnknown()
      .invite(alice)
      .invite(bob)
      .build()

    assertEquals(listOf("2 people were invited to the group."), describeChange(change))
  }

  @Test
  fun unknown_invited_3_persons_and_you() {
    val change = ChangeBuilder.changeByUnknown()
      .invite(alice)
      .invite(you)
      .invite(ACI.from(UUID.randomUUID()))
      .invite(ACI.from(UUID.randomUUID()))
      .build()

    assertEquals(listOf("You were invited to the group.", "3 people were invited to the group."), describeChange(change))
  }

  @Test
  fun unknown_editor_invited_3_persons_and_you_inviter_known() {
    val change = ChangeBuilder.changeByUnknown()
      .invite(alice)
      .inviteBy(you, bob)
      .invite(ACI.from(UUID.randomUUID()))
      .invite(ACI.from(UUID.randomUUID()))
      .build()

    assertEquals(listOf("Bob invited you to the group.", "3 people were invited to the group."), describeChange(change))
  }

  @Test
  fun member_invited_3_persons_and_you_and_added_another_where_you_were_not_first() {
    val change = ChangeBuilder.changeBy(bob)
      .addMember(alice)
      .invite(you)
      .invite(ACI.from(UUID.randomUUID()))
      .invite(ACI.from(UUID.randomUUID()))
      .build()

    assertEquals(listOf("Bob invited you to the group.", "Bob added Alice.", "Bob invited 2 people to the group."), describeChange(change))
  }

  @Test
  fun unknown_editor_but_known_invitee_invited_you_and_added_another_where_you_were_not_first() {
    val change = ChangeBuilder.changeByUnknown()
      .addMember(bob)
      .inviteBy(you, alice)
      .build()

    assertEquals(listOf("Alice invited you to the group.", "Bob joined the group."), describeChange(change))
  }

  @Test
  fun unknown_editor_and_unknown_inviter_invited_you_and_added_another_where_you_were_not_first() {
    val change = ChangeBuilder.changeByUnknown()
      .addMember(alice)
      .invite(you)
      .build()

    assertEquals(listOf("You were invited to the group.", "Alice joined the group."), describeChange(change))
  }

  // Member invitation revocation
  @Test
  fun member_uninvited_1_person() {
    val change = ChangeBuilder.changeBy(alice)
      .uninvite(bob)
      .build()

    assertEquals(listOf("Alice revoked an invitation to the group."), describeChange(change))
  }

  @Test
  fun member_uninvited_2_people() {
    val change = ChangeBuilder.changeBy(alice)
      .uninvite(bob)
      .uninvite(ACI.from(UUID.randomUUID()))
      .build()

    assertEquals(listOf("Alice revoked 2 invitations to the group."), describeChange(change))
  }

  @Test
  fun you_uninvited_1_person() {
    val change = ChangeBuilder.changeBy(you)
      .uninvite(bob)
      .build()

    assertEquals(listOf("You revoked an invitation to the group."), describeChange(change))
  }

  @Test
  fun you_uninvited_2_people() {
    val change = ChangeBuilder.changeBy(you)
      .uninvite(bob)
      .uninvite(ACI.from(UUID.randomUUID()))
      .build()

    assertEquals(listOf("You revoked 2 invitations to the group."), describeChange(change))
  }

  @Test
  fun pending_member_declines_invite() {
    val change = ChangeBuilder.changeBy(bob)
      .uninvite(bob)
      .build()

    assertEquals(listOf("Someone declined an invitation to the group."), describeChange(change))
  }

  @Test
  fun you_decline_invite() {
    val change = ChangeBuilder.changeBy(you)
      .uninvite(you)
      .build()

    assertEquals(listOf("You declined the invitation to the group."), describeChange(change))
  }

  @Test
  fun unknown_revokes_your_invite() {
    val change = ChangeBuilder.changeByUnknown()
      .uninvite(you)
      .build()

    assertEquals(listOf("An admin revoked your invitation to the group."), describeChange(change))
  }

  @Test
  fun unknown_revokes_1_invite() {
    val change = ChangeBuilder.changeByUnknown()
      .uninvite(bob)
      .build()

    assertEquals(listOf("An invitation to the group was revoked."), describeChange(change))
  }

  @Test
  fun unknown_revokes_2_invites() {
    val change = ChangeBuilder.changeByUnknown()
      .uninvite(bob)
      .uninvite(ACI.from(UUID.randomUUID()))
      .build()

    assertEquals(listOf("2 invitations to the group were revoked."), describeChange(change))
  }

  @Test
  fun unknown_revokes_yours_and_three_other_invites() {
    val change = ChangeBuilder.changeByUnknown()
      .uninvite(bob)
      .uninvite(you)
      .uninvite(ACI.from(UUID.randomUUID()))
      .uninvite(ACI.from(UUID.randomUUID()))
      .build()

    assertEquals(listOf("An admin revoked your invitation to the group.", "3 invitations to the group were revoked."), describeChange(change))
  }

  @Test
  fun your_invite_was_revoked_by_known_member() {
    val change = ChangeBuilder.changeBy(bob)
      .uninvite(you)
      .build()

    assertEquals(listOf("Bob revoked your invitation to the group."), describeChange(change))
  }

  // Promote pending members
  @Test
  fun member_accepts_invite() {
    val change = ChangeBuilder.changeBy(bob)
      .promote(bob)
      .build()

    assertEquals(listOf("Bob accepted an invitation to the group."), describeChange(change))
  }

  @Test
  fun you_accept_invite() {
    val change = ChangeBuilder.changeBy(you)
      .promote(you)
      .build()

    assertEquals(listOf("You accepted the invitation to the group."), describeChange(change))
  }

  @Test
  fun member_promotes_pending_member() {
    val change = ChangeBuilder.changeBy(bob)
      .promote(alice)
      .build()

    assertEquals(listOf("Bob added invited member Alice."), describeChange(change))
  }

  @Test
  fun you_promote_pending_member() {
    val change = ChangeBuilder.changeBy(you)
      .promote(bob)
      .build()

    assertEquals(listOf("You added invited member Bob."), describeChange(change))
  }

  @Test
  fun member_promotes_you() {
    val change = ChangeBuilder.changeBy(bob)
      .promote(you)
      .build()

    assertEquals(listOf("Bob added you to the group."), describeChange(change))
  }

  @Test
  fun unknown_added_by_invite() {
    val change = ChangeBuilder.changeByUnknown()
      .promote(you)
      .build()

    assertEquals(listOf("You joined the group."), describeChange(change))
  }

  @Test
  fun unknown_promotes_pending_member() {
    val change = ChangeBuilder.changeByUnknown()
      .promote(alice)
      .build()

    assertEquals(listOf("Alice joined the group."), describeChange(change))
  }

  // Title change
  @Test
  fun member_changes_title() {
    val change = ChangeBuilder.changeBy(alice)
      .title("New title")
      .build()

    assertEquals(listOf("Alice changed the group name to \"" + BidiUtil.isolateBidi("New title") + "\"."), describeChange(change))
  }

  @Test
  fun you_change_title() {
    val change = ChangeBuilder.changeBy(you)
      .title("Title 2")
      .build()

    assertEquals(listOf("You changed the group name to \"" + BidiUtil.isolateBidi("Title 2") + "\"."), describeChange(change))
  }

  @Test
  fun unknown_changed_title() {
    val change = ChangeBuilder.changeByUnknown()
      .title("Title 3")
      .build()

    assertEquals(listOf("The group name has changed to \"" + BidiUtil.isolateBidi("Title 3") + "\"."), describeChange(change))
  }

  // Avatar change
  @Test
  fun member_changes_avatar() {
    val change = ChangeBuilder.changeBy(alice)
      .avatar("Avatar1")
      .build()

    assertEquals(listOf("Alice changed the group avatar."), describeChange(change))
  }

  @Test
  fun you_change_avatar() {
    val change = ChangeBuilder.changeBy(you)
      .avatar("Avatar2")
      .build()

    assertEquals(listOf("You changed the group avatar."), describeChange(change))
  }

  @Test
  fun unknown_changed_avatar() {
    val change = ChangeBuilder.changeByUnknown()
      .avatar("Avatar3")
      .build()

    assertEquals(listOf("The group avatar has been changed."), describeChange(change))
  }

  // Timer change
  @Test
  fun member_changes_timer() {
    val change = ChangeBuilder.changeBy(bob)
      .timer(10)
      .build()

    assertEquals(listOf("Bob set the disappearing message timer to 10 seconds."), describeChange(change))
  }

  @Test
  fun you_change_timer() {
    val change = ChangeBuilder.changeBy(you)
      .timer(60)
      .build()

    assertEquals(listOf("You set the disappearing message timer to 1 minute."), describeChange(change))
  }

  @Test
  fun unknown_change_timer() {
    val change = ChangeBuilder.changeByUnknown()
      .timer(120)
      .build()

    assertEquals(listOf("The disappearing message timer has been set to 2 minutes."), describeChange(change))
  }

  @Test
  fun unknown_change_timer_mentions_no_one() {
    val change = ChangeBuilder.changeByUnknown()
      .timer(120)
      .build()

    assertSingleChangeMentioning(change, emptyList<ACI>())
  }

  // Attribute access change
  @Test
  fun member_changes_attribute_access() {
    val change = ChangeBuilder.changeBy(bob)
      .attributeAccess(MEMBER)
      .build()

    assertEquals(listOf("Bob changed who can edit group info to \"All members\"."), describeChange(change))
  }

  @Test
  fun you_changed_attribute_access() {
    val change = ChangeBuilder.changeBy(you)
      .attributeAccess(ADMINISTRATOR)
      .build()

    assertEquals(listOf("You changed who can edit group info to \"Only admins\"."), describeChange(change))
  }

  @Test
  fun unknown_changed_attribute_access() {
    val change = ChangeBuilder.changeByUnknown()
      .attributeAccess(ADMINISTRATOR)
      .build()

    assertEquals(listOf("Who can edit group info has been changed to \"Only admins\"."), describeChange(change))
  }

  // Membership access change
  @Test
  fun member_changes_membership_access() {
    val change = ChangeBuilder.changeBy(alice)
      .membershipAccess(ADMINISTRATOR)
      .build()

    assertEquals(listOf("Alice changed who can edit group membership to \"Only admins\"."), describeChange(change))
  }

  @Test
  fun you_changed_membership_access() {
    val change = ChangeBuilder.changeBy(you)
      .membershipAccess(MEMBER)
      .build()

    assertEquals(listOf("You changed who can edit group membership to \"All members\"."), describeChange(change))
  }

  @Test
  fun unknown_changed_membership_access() {
    val change = ChangeBuilder.changeByUnknown()
      .membershipAccess(ADMINISTRATOR)
      .build()

    assertEquals(listOf("Who can edit group membership has been changed to \"Only admins\"."), describeChange(change))
  }

  // Group link access change
  @Test
  fun you_changed_group_link_access_to_any() {
    val change = ChangeBuilder.changeBy(you)
      .inviteLinkAccess(ANY)
      .build()

    assertEquals(listOf("You turned on the group link with admin approval off."), describeChange(change))
  }

  @Test
  fun you_changed_group_link_access_to_administrator_approval() {
    val change = ChangeBuilder.changeBy(you)
      .inviteLinkAccess(ADMINISTRATOR)
      .build()

    assertEquals(listOf("You turned on the group link with admin approval on."), describeChange(change))
  }

  @Test
  fun you_turned_off_group_link_access() {
    val change = ChangeBuilder.changeBy(you)
      .inviteLinkAccess(UNSATISFIABLE)
      .build()

    assertEquals(listOf("You turned off the group link."), describeChange(change))
  }

  @Test
  fun member_changed_group_link_access_to_any() {
    val change = ChangeBuilder.changeBy(alice)
      .inviteLinkAccess(ANY)
      .build()

    assertEquals(listOf("Alice turned on the group link with admin approval off."), describeChange(change))
  }

  @Test
  fun member_changed_group_link_access_to_administrator_approval() {
    val change = ChangeBuilder.changeBy(bob)
      .inviteLinkAccess(ADMINISTRATOR)
      .build()

    assertEquals(listOf("Bob turned on the group link with admin approval on."), describeChange(change))
  }

  @Test
  fun member_turned_off_group_link_access() {
    val change = ChangeBuilder.changeBy(alice)
      .inviteLinkAccess(UNSATISFIABLE)
      .build()

    assertEquals(listOf("Alice turned off the group link."), describeChange(change))
  }

  @Test
  fun unknown_changed_group_link_access_to_any() {
    val change = ChangeBuilder.changeByUnknown()
      .inviteLinkAccess(ANY)
      .build()

    assertEquals(listOf("The group link has been turned on with admin approval off."), describeChange(change))
  }

  @Test
  fun unknown_changed_group_link_access_to_administrator_approval() {
    val change = ChangeBuilder.changeByUnknown()
      .inviteLinkAccess(ADMINISTRATOR)
      .build()

    assertEquals(listOf("The group link has been turned on with admin approval on."), describeChange(change))
  }

  @Test
  fun unknown_turned_off_group_link_access() {
    val change = ChangeBuilder.changeByUnknown()
      .inviteLinkAccess(UNSATISFIABLE)
      .build()

    assertEquals(listOf("The group link has been turned off."), describeChange(change))
  }

  // Group link with known previous group state
  @Test
  fun group_link_access_from_unknown_to_administrator() {
    assertEquals("You turned on the group link with admin approval on.", describeGroupLinkChange(you, UNKNOWN, ADMINISTRATOR))
    assertEquals("Alice turned on the group link with admin approval on.", describeGroupLinkChange(alice, UNKNOWN, ADMINISTRATOR))
    assertEquals("The group link has been turned on with admin approval on.", describeGroupLinkChange(null, UNKNOWN, ADMINISTRATOR))
  }

  @Test
  fun group_link_access_from_administrator_to_unsatisfiable() {
    assertEquals("You turned off the group link.", describeGroupLinkChange(you, ADMINISTRATOR, UNSATISFIABLE))
    assertEquals("Bob turned off the group link.", describeGroupLinkChange(bob, ADMINISTRATOR, UNSATISFIABLE))
    assertEquals("The group link has been turned off.", describeGroupLinkChange(null, ADMINISTRATOR, UNSATISFIABLE))
  }

  @Test
  fun group_link_access_from_unsatisfiable_to_administrator() {
    assertEquals("You turned on the group link with admin approval on.", describeGroupLinkChange(you, UNSATISFIABLE, ADMINISTRATOR))
    assertEquals("Alice turned on the group link with admin approval on.", describeGroupLinkChange(alice, UNSATISFIABLE, ADMINISTRATOR))
    assertEquals("The group link has been turned on with admin approval on.", describeGroupLinkChange(null, UNSATISFIABLE, ADMINISTRATOR))
  }

  @Test
  fun group_link_access_from_administrator_to_any() {
    assertEquals("You turned off admin approval for the group link.", describeGroupLinkChange(you, ADMINISTRATOR, ANY))
    assertEquals("Bob turned off admin approval for the group link.", describeGroupLinkChange(bob, ADMINISTRATOR, ANY))
    assertEquals("The admin approval for the group link has been turned off.", describeGroupLinkChange(null, ADMINISTRATOR, ANY))
  }

  @Test
  fun group_link_access_from_any_to_administrator() {
    assertEquals("You turned on admin approval for the group link.", describeGroupLinkChange(you, ANY, ADMINISTRATOR))
    assertEquals("Bob turned on admin approval for the group link.", describeGroupLinkChange(bob, ANY, ADMINISTRATOR))
    assertEquals("The admin approval for the group link has been turned on.", describeGroupLinkChange(null, ANY, ADMINISTRATOR))
  }

  private fun describeGroupLinkChange(editor: ACI?, fromAccess: AccessRequired, toAccess: AccessRequired): String {
    val previousGroupState = DecryptedGroup.Builder()
      .accessControl(
        AccessControl.Builder()
          .addFromInviteLink(fromAccess)
          .build()
      )
      .build()
    val change = (if (editor != null) ChangeBuilder.changeBy(editor) else ChangeBuilder.changeByUnknown()).inviteLinkAccess(toAccess)
      .build()

    val strings = describeChange(previousGroupState, change)
    return strings.single()
  }

  // Group link reset
  @Test
  fun you_reset_group_link() {
    val change = ChangeBuilder.changeBy(you)
      .resetGroupLink()
      .build()

    assertEquals(listOf("You reset the group link."), describeChange(change))
  }

  @Test
  fun member_reset_group_link() {
    val change = ChangeBuilder.changeBy(alice)
      .resetGroupLink()
      .build()

    assertEquals(listOf("Alice reset the group link."), describeChange(change))
  }

  @Test
  fun unknown_reset_group_link() {
    val change = ChangeBuilder.changeByUnknown()
      .resetGroupLink()
      .build()

    assertEquals(listOf("The group link has been reset."), describeChange(change))
  }

  /**
   * When the group link is turned on and reset in the same change, assume this is the first time
   * the link password it being set and do not show reset message.
   */
  @Test
  fun member_changed_group_link_access_to_on_and_reset() {
    val change = ChangeBuilder.changeBy(alice)
      .inviteLinkAccess(ANY)
      .resetGroupLink()
      .build()

    assertEquals(listOf("Alice turned on the group link with admin approval off."), describeChange(change))
  }

  /**
   * When the group link is turned on and reset in the same change, assume this is the first time
   * the link password it being set and do not show reset message.
   */
  @Test
  fun you_changed_group_link_access_to_on_and_reset() {
    val change = ChangeBuilder.changeBy(you)
      .inviteLinkAccess(ADMINISTRATOR)
      .resetGroupLink()
      .build()

    assertEquals(listOf("You turned on the group link with admin approval on."), describeChange(change))
  }

  @Test
  fun you_changed_group_link_access_to_off_and_reset() {
    val change = ChangeBuilder.changeBy(you)
      .inviteLinkAccess(UNSATISFIABLE)
      .resetGroupLink()
      .build()

    assertEquals(listOf("You turned off the group link.", "You reset the group link."), describeChange(change))
  }

  // Group link request
  @Test
  fun you_requested_to_join_the_group() {
    val change = ChangeBuilder.changeBy(you)
      .requestJoin()
      .build()

    assertEquals(listOf("You sent a request to join the group."), describeChange(change))
  }

  @Test
  fun member_requested_to_join_the_group() {
    val change = ChangeBuilder.changeBy(bob)
      .requestJoin()
      .build()

    assertEquals(listOf("Bob requested to join via the group link."), describeChange(change))
  }

  @Test
  fun unknown_requested_to_join_the_group() {
    val change = ChangeBuilder.changeByUnknown()
      .requestJoin(alice)
      .build()

    assertEquals(listOf("Alice requested to join via the group link."), describeChange(change))
  }

  @Test
  fun member_approved_your_join_request() {
    val change = ChangeBuilder.changeBy(bob)
      .approveRequest(you)
      .build()

    assertEquals(listOf("Bob approved your request to join the group."), describeChange(change))
  }

  @Test
  fun member_approved_another_join_request() {
    val change = ChangeBuilder.changeBy(alice)
      .approveRequest(bob)
      .build()

    assertEquals(listOf("Alice approved a request to join the group from Bob."), describeChange(change))
  }

  @Test
  fun you_approved_another_join_request() {
    val change = ChangeBuilder.changeBy(you)
      .approveRequest(alice)
      .build()

    assertEquals(listOf("You approved a request to join the group from Alice."), describeChange(change))
  }

  @Test
  fun unknown_approved_your_join_request() {
    val change = ChangeBuilder.changeByUnknown()
      .approveRequest(you)
      .build()

    assertEquals(listOf("Your request to join the group has been approved."), describeChange(change))
  }

  @Test
  fun unknown_approved_another_join_request() {
    val change = ChangeBuilder.changeByUnknown()
      .approveRequest(bob)
      .build()

    assertEquals(listOf("A request to join the group from Bob has been approved."), describeChange(change))
  }

  @Test
  fun member_denied_another_join_request() {
    val change = ChangeBuilder.changeBy(alice)
      .denyRequest(bob)
      .build()

    assertEquals(listOf("Alice denied a request to join the group from Bob."), describeChange(change))
  }

  @Test
  fun member_denied_your_join_request() {
    val change = ChangeBuilder.changeBy(alice)
      .denyRequest(you)
      .build()

    assertEquals(listOf("Your request to join the group has been denied by an admin."), describeChange(change))
  }

  @Test
  fun you_cancelled_your_join_request() {
    val change = ChangeBuilder.changeBy(you)
      .denyRequest(you)
      .build()

    assertEquals(listOf("You canceled your request to join the group."), describeChange(change))
  }

  @Test
  fun member_cancelled_their_join_request() {
    val change = ChangeBuilder.changeBy(alice)
      .denyRequest(alice)
      .build()

    assertEquals(listOf("Alice canceled their request to join the group."), describeChange(change))
  }

  @Test
  fun unknown_denied_your_join_request() {
    val change = ChangeBuilder.changeByUnknown()
      .denyRequest(you)
      .build()

    assertEquals(listOf("Your request to join the group has been denied by an admin."), describeChange(change))
  }

  @Test
  fun unknown_denied_another_join_request() {
    val change = ChangeBuilder.changeByUnknown()
      .denyRequest(bob)
      .build()

    assertEquals(listOf("A request to join the group from Bob has been denied."), describeChange(change))
  }

  // Multiple changes
  @Test
  fun multiple_changes() {
    val change = ChangeBuilder.changeBy(alice)
      .addMember(bob)
      .membershipAccess(MEMBER)
      .title("Title")
      .addMember(you)
      .timer(300)
      .build()

    assertEquals(
      listOf(
        "Alice added you to the group.",
        "Alice added Bob.",
        "Alice changed the group name to \"" + BidiUtil.isolateBidi("Title") + "\".",
        "Alice set the disappearing message timer to 5 minutes.",
        "Alice changed who can edit group membership to \"All members\"."
      ),
      describeChange(change)
    )
  }

  @Test
  fun multiple_changes_leave_and_promote() {
    val change = ChangeBuilder.changeBy(alice)
      .deleteMember(alice)
      .promoteToAdmin(bob)
      .build()

    assertEquals(
      listOf(
        "Alice made Bob an admin.",
        "Alice left the group."
      ),
      describeChange(change)
    )
  }

  @Test
  fun multiple_changes_leave_and_promote_by_unknown() {
    val change = ChangeBuilder.changeByUnknown()
      .deleteMember(alice)
      .promoteToAdmin(bob)
      .build()

    assertEquals(
      listOf(
        "Bob is now an admin.",
        "Alice is no longer in the group."
      ),
      describeChange(change)
    )
  }

  @Test
  fun multiple_changes_by_unknown() {
    val change = ChangeBuilder.changeByUnknown()
      .addMember(bob)
      .membershipAccess(MEMBER)
      .title("Title 2")
      .avatar("Avatar 1")
      .timer(600)
      .build()

    assertEquals(
      listOf(
        "Bob joined the group.",
        "The group name has changed to \"" + BidiUtil.isolateBidi("Title 2") + "\".",
        "The group avatar has been changed.",
        "The disappearing message timer has been set to 10 minutes.",
        "Who can edit group membership has been changed to \"All members\"."
      ),
      describeChange(change)
    )
  }

  @Test
  fun multiple_changes_join_and_leave_by_unknown() {
    val change = ChangeBuilder.changeByUnknown()
      .addMember(alice)
      .promoteToAdmin(alice)
      .deleteMember(alice)
      .title("Updated title")
      .build()

    assertEquals(
      listOf(
        "Alice joined the group.",
        "Alice is now an admin.",
        "The group name has changed to \"" + BidiUtil.isolateBidi("Updated title") + "\".",
        "Alice is no longer in the group."
      ),
      describeChange(change)
    )
  }

  // Group state without a change record
  @Test
  fun you_created_a_group_change_not_found() {
    val group = newGroupBy(you, 0)
      .build()

    assertEquals("You joined the group.", describeNewGroup(group))
  }

  @Test
  fun you_created_a_group() {
    val group = newGroupBy(you, 0)
      .build()

    val change = ChangeBuilder.changeBy(you)
      .addMember(alice)
      .addMember(you)
      .addMember(bob)
      .title("New title")
      .build()

    assertEquals("You created the group.", describeNewGroup(group, change))
  }

  @Test
  fun alice_created_a_group_change_not_found() {
    val group = newGroupBy(alice, 0)
      .member(you)
      .build()

    assertEquals("You joined the group.", describeNewGroup(group))
  }

  @Test
  fun alice_created_a_group() {
    val group = newGroupBy(alice, 0)
      .member(you)
      .build()

    val change = ChangeBuilder.changeBy(alice)
      .addMember(you)
      .addMember(alice)
      .addMember(bob)
      .title("New title")
      .build()

    assertEquals("Alice added you to the group.", describeNewGroup(group, change))
  }

  @Test
  fun alice_created_a_group_above_zero() {
    val group = newGroupBy(alice, 1)
      .member(you)
      .build()

    assertEquals("You joined the group.", describeNewGroup(group))
  }

  @Test
  fun you_were_invited_to_a_group() {
    val group = newGroupBy(alice, 0)
      .invite(bob, you)
      .build()

    assertEquals("Bob invited you to the group.", describeNewGroup(group))
  }

  @Test
  fun describe_a_group_you_are_not_in() {
    val group = newGroupBy(alice, 1)
      .build()

    assertEquals("Group updated.", describeNewGroup(group))
  }

  @Test
  fun makeRecipientsClickable_onePlaceholder() {
    val id = RecipientId.from(1)

    val result = GroupsV2UpdateMessageProducer.makeRecipientsClickable(
      /* context = */
      ApplicationProvider.getApplicationContext(),
      /* template = */
      GroupsV2UpdateMessageProducer.makePlaceholder(id),
      /* recipientIds = */
      listOf(id),
      /* clickHandler = */
      null
    )

    assertEquals("Alice", result.toString())
  }

  @Test
  fun makeRecipientsClickable_twoPlaceholders_sameRecipient() {
    val id = RecipientId.from(1)
    val placeholder = GroupsV2UpdateMessageProducer.makePlaceholder(id)

    val result = GroupsV2UpdateMessageProducer.makeRecipientsClickable(
      /* context = */
      ApplicationProvider.getApplicationContext(),
      /* template = */
      "$placeholder $placeholder",
      /* recipientIds = */
      listOf(id),
      /* clickHandler = */
      null
    )

    assertEquals("Alice Alice", result.toString())
  }

  @Test
  fun makeRecipientsClickable_twoPlaceholders_differentRecipient() {
    val id1 = RecipientId.from(1)
    val id2 = RecipientId.from(2)

    val placeholder1 = GroupsV2UpdateMessageProducer.makePlaceholder(id1)
    val placeholder2 = GroupsV2UpdateMessageProducer.makePlaceholder(id2)

    val result = GroupsV2UpdateMessageProducer.makeRecipientsClickable(
      /* context = */
      ApplicationProvider.getApplicationContext(),
      /* template = */
      "$placeholder1 $placeholder2",
      /* recipientIds = */
      listOf(id1, id2),
      /* clickHandler = */
      null
    )

    assertEquals("Alice Bob", result.toString())
  }

  @Test
  fun makeRecipientsClickable_complicated() {
    val id1 = RecipientId.from(1)
    val id2 = RecipientId.from(2)

    val placeholder1 = GroupsV2UpdateMessageProducer.makePlaceholder(id1)
    val placeholder2 = GroupsV2UpdateMessageProducer.makePlaceholder(id2)

    val result = GroupsV2UpdateMessageProducer.makeRecipientsClickable(
      /* context = */
      ApplicationProvider.getApplicationContext(),
      /* template = */
      "$placeholder1 said hello to $placeholder2, and $placeholder2 said hello back to $placeholder1.",
      /* recipientIds = */
      listOf(id1, id2),
      /* clickHandler = */
      null
    )

    assertEquals("Alice said hello to Bob, and Bob said hello back to Alice.", result.toString())
  }

  private fun describeConvertedNewGroup(groupState: DecryptedGroup, groupChange: DecryptedGroupChange): String {
    val update = translateDecryptedChangeNewGroup(
      selfIds,
      DecryptedGroupV2Context.Builder()
        .change(groupChange)
        .groupState(groupState)
        .build()
    )

    return producer.describeChanges(update.updates).single().spannable.toString()
  }

  private fun describeConvertedChange(previousGroupState: DecryptedGroup?, change: DecryptedGroupChange): List<String> {
    val update = translateDecryptedChangeUpdate(
      selfIds,
      DecryptedGroupV2Context.Builder()
        .change(change)
        .previousGroupState(previousGroupState)
        .build()
    )

    return producer.describeChanges(update.updates)
      .map { it.spannable }
      .map { it.toString() }
      .toList()
  }

  private fun describeChange(change: DecryptedGroupChange): List<String> {
    return describeChange(null, change)
  }

  private fun describeChange(
    previousGroupState: DecryptedGroup?,
    change: DecryptedGroupChange
  ): List<String> {
    val convertedChange = describeConvertedChange(previousGroupState, change)
    val describedChange = producer.describeChanges(previousGroupState, change)
      .map { it.spannable }
      .map { it.toString() }
      .toList()
    assertEquals(describedChange.size, convertedChange.size)

    val convertedIterator = convertedChange.listIterator()
    val describedIterator = describedChange.listIterator()

    while (convertedIterator.hasNext()) {
      assertEquals(describedIterator.next(), convertedIterator.next())
    }
    return describedChange
  }

  private fun describeNewGroup(group: DecryptedGroup, groupChange: DecryptedGroupChange = DecryptedGroupChange()): String {
    val newGroupString = producer.describeNewGroup(group, groupChange).spannable.toString()
    val convertedGroupString = describeConvertedNewGroup(group, groupChange)

    assertEquals(newGroupString, convertedGroupString)

    return newGroupString
  }

  private fun assertSingleChangeMentioning(change: DecryptedGroupChange, expectedMentions: List<ACI?>) {
    val changes = producer.describeChanges(null, change)

    val description = changes.single()
    assertEquals(expectedMentions, description.mentioned)

    if (expectedMentions.isEmpty()) {
      assertTrue(description.isStringStatic)
    } else {
      assertFalse(description.isStringStatic)
    }
  }

  private class GroupStateBuilder(foundingMember: ACI, revision: Int) {
    private val builder = DecryptedGroup.Builder()
      .revision(revision)
      .members(listOf(DecryptedMember.Builder().aciBytes(foundingMember.toByteString()).build()))

    fun invite(inviter: ACI, invitee: ServiceId): GroupStateBuilder {
      builder.pendingMembers(builder.pendingMembers.plus(DecryptedPendingMember.Builder().serviceIdBytes(invitee.toByteString()).addedByAci(inviter.toByteString()).build()))
      return this
    }

    fun member(member: ACI): GroupStateBuilder {
      builder.members(builder.members.plus(DecryptedMember.Builder().aciBytes(member.toByteString()).build()))
      return this
    }

    fun build(): DecryptedGroup {
      return builder.build()
    }
  }

  companion object {
    private fun recipientWithName(id: RecipientId, name: String): Recipient {
      return mockk<Recipient> {
        every { this@mockk.id } returns id
        every { getDisplayName(any()) } returns name
      }
    }

    private fun newGroupBy(foundingMember: ACI, revision: Int): GroupStateBuilder {
      return GroupStateBuilder(foundingMember, revision)
    }
  }
}
