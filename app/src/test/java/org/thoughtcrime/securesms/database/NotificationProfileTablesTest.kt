package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.single
import io.mockk.every
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.UuidUtil
import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.storage.SignalNotificationProfileRecord
import org.whispersystems.signalservice.api.storage.StorageId
import java.time.DayOfWeek
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.whispersystems.signalservice.internal.storage.protos.NotificationProfile as RemoteNotificationProfile
import org.whispersystems.signalservice.internal.storage.protos.Recipient as RemoteRecipient

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class NotificationProfileTablesTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var alice: RecipientId
  private lateinit var profile1: NotificationProfile

  @Before
  fun setUp() {
    every { RemoteConfig.messageQueueTime } returns TimeUnit.DAYS.toMillis(45)

    alice = SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID()))

    profile1 = NotificationProfile(
      id = 1,
      name = "profile1",
      emoji = "",
      createdAt = 1000L,
      schedule = NotificationProfileSchedule(id = 1),
      allowedMembers = setOf(alice),
      notificationProfileId = NotificationProfileId.generate(),
      deletedTimestampMs = 0,
      storageServiceId = StorageId.forNotificationProfile(byteArrayOf(1, 2, 3))
    )

    SignalDatabase.notificationProfiles.writableDatabase.deleteAll(NotificationProfileTables.NotificationProfileTable.TABLE_NAME)
    SignalDatabase.notificationProfiles.writableDatabase.deleteAll(NotificationProfileTables.NotificationProfileScheduleTable.TABLE_NAME)
    SignalDatabase.notificationProfiles.writableDatabase.deleteAll(NotificationProfileTables.NotificationProfileAllowedMembersTable.TABLE_NAME)
  }

  @Test
  fun `addProfile for profile with empty schedule and members`() {
    val profile: NotificationProfile = SignalDatabase.notificationProfiles.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile

    assertThat(profile.id).isEqualTo(1)
    assertThat(profile.name).isEqualTo("Profile")
    assertThat(profile.emoji).isEqualTo("avatar")
    assertThat(profile.createdAt).isEqualTo(1000L)
    assertThat(profile.schedule.id).isEqualTo(1)

    val profiles = SignalDatabase.notificationProfiles.getProfiles()

    assertThat(profiles)
      .single()
      .transform {
        assertThat(it.id).isEqualTo(1)
        assertThat(it.name).isEqualTo("Profile")
        assertThat(it.emoji).isEqualTo("avatar")
        assertThat(it.createdAt).isEqualTo(1000L)
        assertThat(it.schedule.id).isEqualTo(1)
      }
  }

  @Test
  fun `updateProfile changes all updateable fields`() {
    val profile: NotificationProfile = SignalDatabase.notificationProfiles.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile

    val updatedProfile = SignalDatabase.notificationProfiles.updateProfile(
      profile.copy(
        name = "Profile 2",
        emoji = "avatar 2",
        allowAllCalls = true,
        allowAllMentions = true
      )
    ).profile

    assertThat(updatedProfile.name).isEqualTo("Profile 2")
    assertThat(updatedProfile.emoji).isEqualTo("avatar 2")
    assertThat(updatedProfile.createdAt).isEqualTo(1000L)
    assertThat(updatedProfile.allowAllCalls).isTrue()
    assertThat(updatedProfile.allowAllMentions).isTrue()
  }

  @Test
  fun `when allowed recipients change profile changes`() {
    val profile: NotificationProfile = SignalDatabase.notificationProfiles.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile
    assertThat(profile.isRecipientAllowed(RecipientId.from(1))).isFalse()

    var updated = SignalDatabase.notificationProfiles.addAllowedRecipient(profile.id, RecipientId.from(1))
    assertThat(updated.isRecipientAllowed(RecipientId.from(1))).isTrue()

    updated = SignalDatabase.notificationProfiles.removeAllowedRecipient(profile.id, RecipientId.from(1))
    assertThat(updated.isRecipientAllowed(RecipientId.from(1))).isFalse()

    updated = SignalDatabase.notificationProfiles.updateProfile(updated.copy(allowedMembers = setOf(RecipientId.from(1)))).profile
    assertThat(updated.isRecipientAllowed(RecipientId.from(1))).isTrue()

    updated = SignalDatabase.notificationProfiles.updateProfile(updated.copy(allowedMembers = emptySet())).profile
    assertThat(updated.isRecipientAllowed(RecipientId.from(1))).isFalse()
  }

  @Test
  fun `when schedule change profile changes`() {
    val profile: NotificationProfile = SignalDatabase.notificationProfiles.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile
    assertThat(profile.schedule.enabled).isFalse()
    assertThat(profile.schedule.start).isEqualTo(900)
    assertThat(profile.schedule.end).isEqualTo(1700)
    assertThat(profile.schedule.daysEnabled, "Contains correct default days")
      .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    SignalDatabase.notificationProfiles.updateSchedule(
      profile.schedule.copy(
        enabled = true,
        start = 800,
        end = 1800,
        daysEnabled = setOf(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY)
      )
    )
    var updated = SignalDatabase.notificationProfiles.getProfile(profile.id)!!
    assertThat(updated.schedule.enabled).isTrue()
    assertThat(updated.schedule.start).isEqualTo(800)
    assertThat(updated.schedule.end).isEqualTo(1800)
    assertThat(updated.schedule.daysEnabled, "Contains updated days days")
      .containsExactlyInAnyOrder(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY)

    SignalDatabase.notificationProfiles.updateSchedule(profile.schedule)
    updated = SignalDatabase.notificationProfiles.getProfile(profile.id)!!
    assertThat(updated.schedule.enabled).isFalse()
    assertThat(updated.schedule.start).isEqualTo(900)
    assertThat(updated.schedule.end).isEqualTo(1700)
    assertThat(updated.schedule.daysEnabled, "Contains correct default days")
      .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    updated = SignalDatabase.notificationProfiles.updateProfile(
      profile.copy(
        schedule = profile.schedule.copy(
          enabled = true,
          start = 800,
          end = 1800,
          daysEnabled = setOf(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY)
        )
      )
    ).profile
    assertThat(updated.schedule.enabled).isTrue()
    assertThat(updated.schedule.start).isEqualTo(800)
    assertThat(updated.schedule.end).isEqualTo(1800)
    assertThat(updated.schedule.daysEnabled, "Contains updated days days")
      .containsExactlyInAnyOrder(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY)

    updated = SignalDatabase.notificationProfiles.updateProfile(profile).profile
    assertThat(updated.schedule.enabled).isFalse()
    assertThat(updated.schedule.start).isEqualTo(900)
    assertThat(updated.schedule.end).isEqualTo(1700)
    assertThat(updated.schedule.daysEnabled, "Contains correct default days")
      .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
  }

  @Test
  fun givenARemoteProfile_whenIInsertLocally_thenIExpectAListWithThatProfile() {
    val remoteRecord =
      SignalNotificationProfileRecord(
        profile1.storageServiceId!!,
        RemoteNotificationProfile(
          id = UuidUtil.toByteArray(profile1.notificationProfileId.uuid).toByteString(),
          name = "profile1",
          emoji = "",
          color = profile1.color.colorInt(),
          createdAtMs = 1000L,
          allowedMembers = listOf(RemoteRecipient(RemoteRecipient.Contact(Recipient.resolved(alice).serviceId.get().toString()))),
          allowAllMentions = false,
          allowAllCalls = true,
          scheduleEnabled = false,
          scheduleStartTime = 900,
          scheduleEndTime = 1700,
          scheduleDaysEnabled = emptyList(),
          deletedAtTimestampMs = 0
        )
      )

    SignalDatabase.notificationProfiles.insertNotificationProfileFromStorageSync(remoteRecord)
    val actualProfiles = SignalDatabase.notificationProfiles.getProfiles()

    assertEquals(listOf(profile1), actualProfiles)
  }

  @Test
  fun givenAProfile_whenIDeleteIt_thenIExpectAnEmptyList() {
    val profile: NotificationProfile = SignalDatabase.notificationProfiles.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile

    SignalDatabase.notificationProfiles.deleteProfile(profile.id)

    assertThat(SignalDatabase.notificationProfiles.getProfiles()).isEmpty()
    assertThat(SignalDatabase.notificationProfiles.getProfile(profile.id))
  }

  @Test
  fun givenADeletedProfile_whenIGetIt_thenIExpectItToStillHaveASchedule() {
    val profile: NotificationProfile = SignalDatabase.notificationProfiles.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile

    SignalDatabase.notificationProfiles.deleteProfile(profile.id)

    val deletedProfile = SignalDatabase.notificationProfiles.getProfile(profile.id)!!
    assertThat(deletedProfile.schedule.enabled).isFalse()
    assertThat(deletedProfile.schedule.start).isEqualTo(900)
    assertThat(deletedProfile.schedule.end).isEqualTo(1700)
    assertThat(deletedProfile.schedule.daysEnabled, "Contains correct default days")
      .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
  }

  @Test
  fun givenNotificationProfiles_whenIUpdateTheirStorageSyncIds_thenIExpectAnUpdatedList() {
    SignalDatabase.notificationProfiles.createProfile(
      name = "Profile1",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    )
    SignalDatabase.notificationProfiles.createProfile(
      name = "Profile2",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 2000L
    )

    val existingMap = SignalDatabase.notificationProfiles.getStorageSyncIdsMap()
    existingMap.forEach { (id, _) ->
      SignalDatabase.notificationProfiles.applyStorageIdUpdate(id, StorageId.forNotificationProfile(StorageSyncHelper.generateKey()))
    }
    val updatedMap = SignalDatabase.notificationProfiles.getStorageSyncIdsMap()

    existingMap.forEach { (id, storageId) ->
      assertNotEquals(storageId, updatedMap[id])
    }
  }

  @Test
  fun givenAProfileDeletedOver30Days_whenICleanUp_thenIExpectItToNotHaveAStorageId() {
    val remoteRecord =
      SignalNotificationProfileRecord(
        profile1.storageServiceId!!,
        RemoteNotificationProfile(
          id = UuidUtil.toByteArray(profile1.notificationProfileId.uuid).toByteString(),
          name = "profile1",
          emoji = "",
          color = profile1.color.colorInt(),
          createdAtMs = 1000L,
          deletedAtTimestampMs = 1000L
        )
      )

    SignalDatabase.notificationProfiles.insertNotificationProfileFromStorageSync(remoteRecord)
    SignalDatabase.notificationProfiles.removeStorageIdsFromOldDeletedProfiles(System.currentTimeMillis())
    assertThat(SignalDatabase.notificationProfiles.getStorageSyncIds()).isEmpty()
  }
}

private val NotificationProfileTables.NotificationProfileChangeResult.profile: NotificationProfile
  get() = (this as NotificationProfileTables.NotificationProfileChangeResult.Success).notificationProfile
