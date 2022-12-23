package org.thoughtcrime.securesms.profiles.manage

import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reactivex.rxjava3.schedulers.TestScheduler
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.InstrumentationApplicationDependencyProvider
import org.thoughtcrime.securesms.testing.Put
import org.thoughtcrime.securesms.testing.RxTestSchedulerRule
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIsNotNull
import org.thoughtcrime.securesms.testing.assertIsNull
import org.thoughtcrime.securesms.testing.success
import org.whispersystems.signalservice.internal.push.ReserveUsernameResponse
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class UsernameEditFragmentTest {

  @get:Rule
  val harness = SignalActivityRule(othersCount = 10)

  private val ioScheduler = TestScheduler()
  private val computationScheduler = TestScheduler()

  @get:Rule
  val testSchedulerRule = RxTestSchedulerRule(
    ioTestScheduler = ioScheduler,
    computationTestScheduler = computationScheduler
  )

  @After
  fun tearDown() {
    InstrumentationApplicationDependencyProvider.clearHandlers()
  }

  @Test
  fun testUsernameCreationInRegistration() {
    val scenario = createScenario(true)

    scenario.moveToState(Lifecycle.State.RESUMED)

    onView(withId(R.id.toolbar)).check { view, noViewFoundException ->
      noViewFoundException.assertIsNull()
      val toolbar = view as Toolbar

      toolbar.navigationIcon.assertIsNull()
    }

    onView(withText(R.string.UsernameEditFragment__add_a_username)).check(matches(isDisplayed()))
    onView(withContentDescription(R.string.load_more_header__loading)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)))
  }

  @Ignore("Flakey espresso test.")
  @Test
  fun testUsernameCreationOutsideOfRegistration() {
    val scenario = createScenario()

    scenario.moveToState(Lifecycle.State.RESUMED)

    onView(withId(R.id.toolbar)).check { view, noViewFoundException ->
      noViewFoundException.assertIsNull()
      val toolbar = view as Toolbar

      toolbar.navigationIcon.assertIsNotNull()
    }

    onView(withText(R.string.UsernameEditFragment_username)).check(matches(isDisplayed()))
    onView(withContentDescription(R.string.load_more_header__loading)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)))
  }

  @Ignore("Flakey espresso test.")
  @Test
  fun testNicknameUpdateHappyPath() {
    val nickname = "Spiderman"
    val discriminator = "4578"
    val username = "$nickname${UsernameState.DELIMITER}$discriminator"

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Put("/v1/accounts/username/reserved") {
        MockResponse().success(ReserveUsernameResponse(username, "reservationToken"))
      },
      Put("/v1/accounts/username/confirm") {
        MockResponse().success()
      }
    )

    val scenario = createScenario(isInRegistration = true)
    scenario.moveToState(Lifecycle.State.RESUMED)

    onView(withId(R.id.username_text)).perform(typeText(nickname))

    computationScheduler.advanceTimeBy(501, TimeUnit.MILLISECONDS)
    computationScheduler.triggerActions()

    onView(withContentDescription(R.string.load_more_header__loading)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))

    ioScheduler.triggerActions()
    computationScheduler.triggerActions()

    onView(withId(R.id.username_text)).perform(closeSoftKeyboard())
    onView(withId(R.id.username_done_button)).check(matches(isDisplayed()))
    onView(withId(R.id.username_done_button)).check(matches(isEnabled()))
    onView(withText(username)).check(matches(isDisplayed()))

    onView(withId(R.id.username_done_button)).perform(click())

    computationScheduler.triggerActions()
    onView(withId(R.id.username_done_button)).check(matches(isNotEnabled()))
  }

  private fun createScenario(isInRegistration: Boolean = false): FragmentScenario<UsernameEditFragment> {
    val fragmentArgs = UsernameEditFragmentArgs.Builder().setIsInRegistration(isInRegistration).build().toBundle()
    return launchFragmentInContainer(
      fragmentArgs = fragmentArgs,
      themeResId = R.style.Signal_DayNight_NoActionBar
    )
  }
}
