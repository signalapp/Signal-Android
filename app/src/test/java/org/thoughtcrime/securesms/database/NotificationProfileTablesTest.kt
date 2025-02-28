package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.single
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class NotificationProfileTablesTest {
  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

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
}

private val NotificationProfileTables.NotificationProfileChangeResult.profile: NotificationProfile
  get() = (this as NotificationProfileTables.NotificationProfileChangeResult.Success).notificationProfile
