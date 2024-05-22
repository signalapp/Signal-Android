package org.thoughtcrime.securesms.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.MockApplicationDependencyProvider
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.TestDatabaseUtil
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class NotificationProfileDatabaseTest {

  private lateinit var db: SQLiteDatabase
  private lateinit var database: NotificationProfileDatabase

  @Before
  fun setup() {
    val sqlCipher = TestDatabaseUtil.inMemoryDatabase {
      NotificationProfileDatabase.CREATE_TABLE.forEach {
        println(it)
        this.execSQL(it)
      }
      NotificationProfileDatabase.CREATE_INDEXES.forEach {
        println(it)
        this.execSQL(it)
      }
    }

    if (!AppDependencies.isInitialized) {
      AppDependencies.init(ApplicationProvider.getApplicationContext(), MockApplicationDependencyProvider())
    }

    db = sqlCipher.writableDatabase
    database = NotificationProfileDatabase(ApplicationProvider.getApplicationContext(), sqlCipher)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `addProfile for profile with empty schedule and members`() {
    val profile: NotificationProfile = database.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile

    assertEquals(1, profile.id)
    assertEquals("Profile", profile.name)
    assertEquals("avatar", profile.emoji)
    assertEquals(1000L, profile.createdAt)
    assertEquals(1, profile.schedule.id)

    val profiles = database.getProfiles()

    assertEquals(1, profiles.size)
    assertEquals(1, profiles[0].id)
    assertEquals("Profile", profiles[0].name)
    assertEquals("avatar", profiles[0].emoji)
    assertEquals(1000L, profiles[0].createdAt)
    assertEquals(1, profiles[0].schedule.id)
  }

  @Test
  fun `updateProfile changes all updateable fields`() {
    val profile: NotificationProfile = database.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile

    val updatedProfile = database.updateProfile(
      profile.copy(
        name = "Profile 2",
        emoji = "avatar 2",
        allowAllCalls = true,
        allowAllMentions = true
      )
    ).profile

    assertEquals("Profile 2", updatedProfile.name)
    assertEquals("avatar 2", updatedProfile.emoji)
    assertEquals(1000L, updatedProfile.createdAt)
    assertTrue(updatedProfile.allowAllCalls)
    assertTrue(updatedProfile.allowAllMentions)
  }

  @Test
  fun `when allowed recipients change profile changes`() {
    val profile: NotificationProfile = database.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile
    assertFalse(profile.isRecipientAllowed(RecipientId.from(1)))

    var updated = database.addAllowedRecipient(profile.id, RecipientId.from(1))
    assertTrue(updated.isRecipientAllowed(RecipientId.from(1)))

    updated = database.removeAllowedRecipient(profile.id, RecipientId.from(1))
    assertFalse(updated.isRecipientAllowed(RecipientId.from(1)))

    updated = database.updateProfile(updated.copy(allowedMembers = setOf(RecipientId.from(1)))).profile
    assertTrue(updated.isRecipientAllowed(RecipientId.from(1)))

    updated = database.updateProfile(updated.copy(allowedMembers = emptySet())).profile
    assertFalse(updated.isRecipientAllowed(RecipientId.from(1)))
  }

  @Test
  fun `when schedule change profile changes`() {
    val profile: NotificationProfile = database.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile
    assertFalse(profile.schedule.enabled)
    assertEquals(900, profile.schedule.start)
    assertEquals(1700, profile.schedule.end)
    assertThat("Contains correct default days", profile.schedule.daysEnabled, containsInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))

    database.updateSchedule(profile.schedule.copy(enabled = true, start = 800, end = 1800, daysEnabled = setOf(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY)))
    var updated = database.getProfile(profile.id)!!
    assertTrue(updated.schedule.enabled)
    assertEquals(800, updated.schedule.start)
    assertEquals(1800, updated.schedule.end)
    assertThat("Contains updated days days", updated.schedule.daysEnabled, containsInAnyOrder(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY))

    database.updateSchedule(profile.schedule)
    updated = database.getProfile(profile.id)!!
    assertFalse(updated.schedule.enabled)
    assertEquals(900, updated.schedule.start)
    assertEquals(1700, updated.schedule.end)
    assertThat("Contains correct default days", updated.schedule.daysEnabled, containsInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))

    updated = database.updateProfile(profile.copy(schedule = profile.schedule.copy(enabled = true, start = 800, end = 1800, daysEnabled = setOf(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY)))).profile
    assertTrue(updated.schedule.enabled)
    assertEquals(800, updated.schedule.start)
    assertEquals(1800, updated.schedule.end)
    assertThat("Contains updated days days", updated.schedule.daysEnabled, containsInAnyOrder(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY))

    updated = database.updateProfile(profile).profile
    assertFalse(updated.schedule.enabled)
    assertEquals(900, updated.schedule.start)
    assertEquals(1700, updated.schedule.end)
    assertThat("Contains correct default days", updated.schedule.daysEnabled, containsInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
  }
}

private val NotificationProfileDatabase.NotificationProfileChangeResult.profile: NotificationProfile
  get() = (this as NotificationProfileDatabase.NotificationProfileChangeResult.Success).notificationProfile
