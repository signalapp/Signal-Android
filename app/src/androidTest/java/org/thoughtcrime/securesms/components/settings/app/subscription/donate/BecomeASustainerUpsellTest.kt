/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.getParcelableExtraCompat
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.self.none.BecomeASustainerFragment
import org.thoughtcrime.securesms.badges.self.overview.BadgesOverviewFragment
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.routes.AppSettingsRoute
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.profiles.manage.EditProfileActivity
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.InAppPaymentsRule
import org.thoughtcrime.securesms.testing.SignalActivityRule
import java.util.concurrent.TimeUnit

/**
 * Entry-path coverage for the "become a subscriber" upsell.
 *
 * The upsell surface is [BecomeASustainerFragment], reached from the profile screen's badges row. The
 * handoff test asserts the sheet's button launches the recurring-donation checkout route; the gating
 * tests assert the "user might be a sustainer" rule in [org.thoughtcrime.securesms.profiles.manage.EditProfileFragment]:
 * a non-donor sees the upsell, an existing donor is routed to badge management instead.
 */
@RunWith(AndroidJUnit4::class)
class BecomeASustainerUpsellTest {

  @get:Rule
  val harness = SignalActivityRule()

  @get:Rule
  val iapRule = InAppPaymentsRule()

  private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun setUp() {
    setSelfBadges(emptyList())
  }

  @Test
  fun nonDonor_badgeTap_showsUpsellSheet() {
    ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
      onView(withId(R.id.manage_profile_badges_container)).perform(scrollTo(), click())

      val sheet = awaitNavHostFragment(scenario) { it.filterIsInstance<BecomeASustainerFragment>().firstOrNull() }
      assertThat(sheet).isNotNull()
    }
  }

  @Test
  fun becomeASustainer_launchesRecurringCheckoutRoute() {
    LaunchedActivityRecorder(AppSettingsActivity::class.java).use { recorder ->
      ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
        onView(withId(R.id.manage_profile_badges_container)).perform(scrollTo(), click())
        awaitNavHostFragment(scenario) { it.filterIsInstance<BecomeASustainerFragment>().firstOrNull() }

        val becomeASustainer = withText(R.string.BecomeASustainerMegaphone__become_a_sustainer)
        onView(withId(R.id.recycler)).perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(becomeASustainer)))
        onView(becomeASustainer).perform(click())

        val intent = recorder.awaitLaunch()
        val route = intent.extras!!.keySet().firstNotNullOfOrNull { intent.getParcelableExtraCompat(it, AppSettingsRoute::class.java) }
        assertThat(route).isEqualTo(AppSettingsRoute.DonationsRoute.Donations(directToCheckoutType = InAppPaymentType.RECURRING_DONATION))
      }
    }
  }

  @Test
  fun donor_badgeTap_opensBadgeManagement() {
    setSelfBadges(listOf(donorBadge()))

    ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
      onView(withId(R.id.manage_profile_badges_container)).perform(scrollTo(), click())

      val badgeManagement = awaitNavHostFragment(scenario) { it.filterIsInstance<BadgesOverviewFragment>().firstOrNull() }
      assertThat(badgeManagement).isNotNull()

      val upsell = navHostFragments(scenario).filterIsInstance<BecomeASustainerFragment>().firstOrNull()
      assertThat(upsell).isNull()
    }
  }

  private fun awaitNavHostFragment(scenario: ActivityScenario<EditProfileActivity>, selector: (List<Fragment>) -> Fragment?): Fragment {
    return await(description = "nav host fragment") { selector(navHostFragments(scenario)) }
  }

  private fun navHostFragments(scenario: ActivityScenario<EditProfileActivity>): List<Fragment> {
    var fragments: List<Fragment> = emptyList()
    scenario.onActivity { activity ->
      val navHost = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
      fragments = navHost?.childFragmentManager?.fragments ?: emptyList()
    }
    return fragments
  }

  private fun setSelfBadges(badges: List<Badge>) {
    SignalDatabase.recipients.setBadges(Recipient.self().id, badges)
    Recipient.self().fresh()
  }

  private fun donorBadge(): Badge {
    return Badge(
      id = "test-donor-badge",
      category = Badge.Category.Donor,
      name = "Signal Sustainer",
      description = "",
      imageUrl = Uri.EMPTY,
      imageDensity = "xxhdpi",
      expirationTimestamp = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30),
      visible = true,
      duration = TimeUnit.DAYS.toMillis(30)
    )
  }

  private fun <T> await(timeoutMs: Long = 10_000, pollMs: Long = 50, description: String, supplier: () -> T?): T {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      supplier()?.let { return it }
      Thread.sleep(pollMs)
    }
    error("Timed out after ${timeoutMs}ms waiting for $description")
  }

  /**
   * Captures the first launched activity of [activityClass] and finishes it immediately so its own
   * downstream launches (e.g. the auto-launched checkout) don't cascade during the test.
   */
  private inner class LaunchedActivityRecorder(private val activityClass: Class<out Activity>) : AutoCloseable {
    @Volatile
    private var launched: Intent? = null

    private val app = context.applicationContext as Application
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activityClass.isInstance(activity) && launched == null) {
          launched = activity.intent
          activity.finish()
        }
      }

      override fun onActivityStarted(activity: Activity) = Unit
      override fun onActivityResumed(activity: Activity) = Unit
      override fun onActivityPaused(activity: Activity) = Unit
      override fun onActivityStopped(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
      override fun onActivityDestroyed(activity: Activity) = Unit
    }

    init {
      app.registerActivityLifecycleCallbacks(callbacks)
    }

    fun awaitLaunch(): Intent = await(description = "${activityClass.simpleName} launch") { launched }

    override fun close() = app.unregisterActivityLifecycleCallbacks(callbacks)
  }
}
