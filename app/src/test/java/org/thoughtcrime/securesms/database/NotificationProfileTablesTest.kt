package org.thoughtcrime.securesms.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.single
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.TestDatabaseUtil
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class NotificationProfileTablesTest {
  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var db: SQLiteDatabase
  private lateinit var database: NotificationProfileTables

  @Before
  fun setup() {
    val sqlCipher = TestDatabaseUtil.inMemoryDatabase {
      NotificationProfileTables.CREATE_TABLE.forEach {
        println(it)
        this.execSQL(it)
      }
      NotificationProfileTables.CREATE_INDEXES.forEach {
        println(it)
        this.execSQL(it)
      }
    }

    db = sqlCipher.myWritableDatabase
    database = NotificationProfileTables(ApplicationProvider.getApplicationContext(), sqlCipher)
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

    assertThat(profile.id).isEqualTo(1)
    assertThat(profile.name).isEqualTo("Profile")
    assertThat(profile.emoji).isEqualTo("avatar")
    assertThat(profile.createdAt).isEqualTo(1000L)
    assertThat(profile.schedule.id).isEqualTo(1)

    val profiles = database.getProfiles()

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

    assertThat(updatedProfile.name).isEqualTo("Profile 2")
    assertThat(updatedProfile.emoji).isEqualTo("avatar 2")
    assertThat(updatedProfile.createdAt).isEqualTo(1000L)
    assertThat(updatedProfile.allowAllCalls).isTrue()
    assertThat(updatedProfile.allowAllMentions).isTrue()
  }

  @Test
  fun `when allowed recipients change profile changes`() {
    val profile: NotificationProfile = database.createProfile(
      name = "Profile",
      emoji = "avatar",
      color = AvatarColor.A210,
      createdAt = 1000L
    ).profile
    assertThat(profile.isRecipientAllowed(RecipientId.from(1))).isFalse()

    var updated = database.addAllowedRecipient(profile.id, RecipientId.from(1))
    assertThat(updated.isRecipientAllowed(RecipientId.from(1))).isTrue()

    updated = database.removeAllowedRecipient(profile.id, RecipientId.from(1))
    assertThat(updated.isRecipientAllowed(RecipientId.from(1))).isFalse()

    updated = database.updateProfile(updated.copy(allowedMembers = setOf(RecipientId.from(1)))).profile
    assertThat(updated.isRecipientAllowed(RecipientId.from(1))).isTrue()

    updated = database.updateProfile(updated.copy(allowedMembers = emptySet())).profile
    assertThat(updated.isRecipientAllowed(RecipientId.from(1))).isFalse()
  }

  @Test
  fun `when schedule change profile changes`() {
    val profile: NotificationProfile = database.createProfile(
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

    database.updateSchedule(
      profile.schedule.copy(
        enabled = true,
        start = 800,
        end = 1800,
        daysEnabled = setOf(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY)
      )
    )
    var updated = database.getProfile(profile.id)!!
    assertThat(updated.schedule.enabled).isTrue()
    assertThat(updated.schedule.start).isEqualTo(800)
    assertThat(updated.schedule.end).isEqualTo(1800)
    assertThat(updated.schedule.daysEnabled, "Contains updated days days")
      .containsExactlyInAnyOrder(DayOfWeek.SUNDAY, DayOfWeek.FRIDAY)

    database.updateSchedule(profile.schedule)
    updated = database.getProfile(profile.id)!!
    assertThat(updated.schedule.enabled).isFalse()
    assertThat(updated.schedule.start).isEqualTo(900)
    assertThat(updated.schedule.end).isEqualTo(1700)
    assertThat(updated.schedule.daysEnabled, "Contains correct default days")
      .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    updated = database.updateProfile(
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

    updated = database.updateProfile(profile).profile
    assertThat(updated.schedule.enabled).isFalse()
    assertThat(updated.schedule.start).isEqualTo(900)
    assertThat(updated.schedule.end).isEqualTo(1700)
    assertThat(updated.schedule.daysEnabled, "Contains correct default days")
      .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
  }
}

private val NotificationProfileTables.NotificationProfileChangeResult.profile: NotificationProfile
  get() = (this as NotificationProfileTables.NotificationProfileChangeResult.Success).notificationProfile
