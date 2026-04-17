/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.hasSize
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class NameCollisionTablesTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var alice: RecipientId
  private lateinit var bob: RecipientId
  private lateinit var charlie: RecipientId

  @Before
  fun setUp() {
    alice = recipients.createRecipient("Buddy #0", profileSharing = false).also { recipients.insertIncomingMessage(it) }
    bob = recipients.createRecipient("Buddy #1", profileSharing = false).also { recipients.insertIncomingMessage(it) }
    charlie = recipients.createRecipient("Buddy #2", profileSharing = false).also { recipients.insertIncomingMessage(it) }
  }

  @Test
  fun givenAUserWithAThreadIdButNoConflicts_whenIGetCollisionsForThreadRecipient_thenIExpectNoCollisions() {
    SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(alice))
    val actual = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)

    assertThat(actual).hasSize(0)
  }

  @Test
  fun givenTwoUsers_whenOneChangesTheirProfileNameToMatchTheOther_thenIExpectANameCollision() {
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Alice", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))

    val actualAlice = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)
    val actualBob = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(bob)

    assertThat(actualAlice).hasSize(2)
    assertThat(actualBob).hasSize(2)
  }

  @Test
  fun givenTwoUsersWithANameCollisions_whenOneChangesToADifferentName_thenIExpectNoNameCollisions() {
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Alice", "Android"))

    val actualAlice = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)
    val actualBob = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(bob)

    assertThat(actualAlice).hasSize(0)
    assertThat(actualBob).hasSize(0)
  }

  @Test
  fun givenThreeUsersWithANameCollisions_whenOneChangesToADifferentName_thenIExpectTwoNameCollisions() {
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(charlie, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Alice", "Android"))

    val actualAlice = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)
    val actualBob = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(bob)
    val actualCharlie = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(charlie)

    assertThat(actualAlice).hasSize(0)
    assertThat(actualBob).hasSize(2)
    assertThat(actualCharlie).hasSize(2)
  }

  @Test
  fun givenTwoUsersWithADismissedNameCollision_whenOneChangesToADifferentNameAndBack_thenIExpectANameCollision() {
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    SignalDatabase.nameCollisions.markCollisionsForThreadRecipientDismissed(alice)

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Alice", "Android"))
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))

    val actualAlice = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)

    assertThat(actualAlice).hasSize(2)
  }

  @Test
  fun givenADismissedNameCollisionForAlice_whenIGetNameCollisionsForAlice_thenIExpectNoNameCollisions() {
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    SignalDatabase.nameCollisions.markCollisionsForThreadRecipientDismissed(alice)

    val actualCollisions = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)

    assertThat(actualCollisions).hasSize(0)
  }

  @Test
  fun givenADismissedNameCollisionForAliceThatIUpdate_whenIGetNameCollisionsForAlice_thenIExpectNoNameCollisions() {
    SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(alice))

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    SignalDatabase.nameCollisions.markCollisionsForThreadRecipientDismissed(alice)
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))

    val actualCollisions = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)

    assertThat(actualCollisions).hasSize(0)
  }

  @Test
  fun givenADismissedNameCollisionForAlice_whenIGetNameCollisionsForBob_thenIExpectANameCollisionWithTwoEntries() {
    SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(alice))

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    SignalDatabase.nameCollisions.markCollisionsForThreadRecipientDismissed(alice)

    val actualCollisions = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(bob)

    assertThat(actualCollisions).hasSize(2)
  }

  @Test
  fun givenAGroupWithAliceAndBob_whenIInsertNameChangeMessageForAlice_thenIExpectAGroupNameCollision() {
    val info = recipients.createGroup(alice, bob)

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))

    SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(info.recipientId))
    SignalDatabase.messages.insertProfileNameChangeMessages(Recipient.resolved(alice), "Bob Android", "Alice Android")

    val collisions = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(info.recipientId)

    assertThat(collisions).hasSize(2)
  }

  @Test
  fun givenAGroupWithAliceAndBobWithDismissedCollision_whenIInsertNameChangeMessageForAlice_thenIExpectAGroupNameCollision() {
    val info = recipients.createGroup(alice, bob)

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))

    SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(info.recipientId))
    SignalDatabase.messages.insertProfileNameChangeMessages(Recipient.resolved(alice), "Bob Android", "Alice Android")
    SignalDatabase.nameCollisions.markCollisionsForThreadRecipientDismissed(info.recipientId)
    SignalDatabase.messages.insertProfileNameChangeMessages(Recipient.resolved(alice), "Bob Android", "Alice Android")

    val collisions = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(info.recipientId)

    assertThat(collisions).hasSize(0)
  }

  @Test
  fun givenAGroupWithAliceAndBob_whenIInsertNameChangeMessageForAliceWithMismatch_thenIExpectNoGroupNameCollision() {
    val info = recipients.createGroup(alice, bob)

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Alice", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))

    SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(info.recipientId))
    SignalDatabase.messages.insertProfileNameChangeMessages(Recipient.resolved(alice), "Alice Android", "Bob Android")

    val collisions = SignalDatabase.nameCollisions.getCollisionsForThreadRecipientId(info.recipientId)

    assertThat(collisions).hasSize(0)
  }

  private fun setProfileNameAndCheckCollision(recipientId: RecipientId, name: ProfileName) {
    recipients.setProfileName(recipientId, name)
    SignalDatabase.nameCollisions.handleIndividualNameCollision(recipientId)
  }
}
