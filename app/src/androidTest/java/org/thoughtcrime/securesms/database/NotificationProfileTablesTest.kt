package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.storage.SignalNotificationProfileRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.time.DayOfWeek
import java.util.UUID
import org.whispersystems.signalservice.internal.storage.protos.NotificationProfile as RemoteNotificationProfile
import org.whispersystems.signalservice.internal.storage.protos.Recipient as RemoteRecipient

@RunWith(AndroidJUnit4::class)
class NotificationProfileTablesTest {

  @get:Rule
  val harness = SignalActivityRule()

  private lateinit var alice: RecipientId
  private lateinit var profile1: NotificationProfile

  @Before
  fun setUp() {
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

  private val NotificationProfileTables.NotificationProfileChangeResult.profile: NotificationProfile
    get() = (this as NotificationProfileTables.NotificationProfileChangeResult.Success).notificationProfile
}
