package network.loki.messenger

import android.content.ClipboardManager
import android.content.Context
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import network.loki.messenger.util.InputBarButtonDrawableMatcher.Companion.inputButtonWithDrawable
import network.loki.messenger.util.NewConversationButtonDrawableMatcher.Companion.newConversationButtonWithDrawable
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.home.HomeActivity

@RunWith(AndroidJUnit4::class)
@LargeTest
class HomeActivityTests {

    @get:Rule
    var activityRule = ActivityScenarioRule(HomeActivity::class.java)

    private fun sendMessage(messageToSend: String) {
        // assume in chat activity
        onView(allOf(isDescendantOfA(withId(R.id.inputBar)),withId(R.id.inputBarEditText))).perform(ViewActions.replaceText(messageToSend))
        onView(allOf(isDescendantOfA(withId(R.id.inputBar)),inputButtonWithDrawable(R.drawable.ic_arrow_up))).perform(ViewActions.click())
    }

    private fun setupLoggedInState(hasViewedSeed: Boolean = false) {
        // landing activity
        onView(withId(R.id.registerButton)).perform(ViewActions.click())
        // session ID - register activity
        onView(withId(R.id.registerButton)).perform(ViewActions.click())
        // display name selection
        onView(withId(R.id.displayNameEditText)).perform(ViewActions.typeText("test-user123"))
        onView(withId(R.id.registerButton)).perform(ViewActions.click())
        // PN select
        if (hasViewedSeed) {
            // has viewed seed is set to false after register activity
            TextSecurePreferences.setHasViewedSeed(InstrumentationRegistry.getInstrumentation().targetContext, true)
        }
        onView(withId(R.id.backgroundPollingOptionView)).perform(ViewActions.click())
        onView(withId(R.id.registerButton)).perform(ViewActions.click())
    }

    private fun goToMyChat() {
        onView(newConversationButtonWithDrawable(R.drawable.ic_plus)).perform(ViewActions.click())
        onView(newConversationButtonWithDrawable(R.drawable.ic_message)).perform(ViewActions.click())
        // new chat
        onView(withId(R.id.publicKeyEditText)).perform(ViewActions.closeSoftKeyboard())
        onView(withId(R.id.copyButton)).perform(ViewActions.click())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var copied: String
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            copied = clipboardManager.primaryClip!!.getItemAt(0).text.toString()
        }
        onView(withId(R.id.publicKeyEditText)).perform(ViewActions.typeText(copied))
        onView(withId(R.id.createPrivateChatButton)).perform(ViewActions.click())
    }

    @Test
    fun testLaunches_dismiss_seedView() {
        setupLoggedInState()
        onView(allOf(withId(R.id.button), isDescendantOfA(withId(R.id.seedReminderView)))).perform(ViewActions.click())
        onView(withId(R.id.copyButton)).perform(ViewActions.click())
        pressBack()
        onView(withId(R.id.seedReminderView)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun testIsVisible_seedView() {
        setupLoggedInState()
        onView(withId(R.id.seedReminderView)).check(matches(isCompletelyDisplayed()))
    }

    @Test
    fun testIsVisible_alreadyDismissed_seedView() {
        setupLoggedInState(hasViewedSeed = true)
        onView(withId(R.id.seedReminderView)).check(doesNotExist())
    }

    @Test
    fun testChat_withSelf() {
        setupLoggedInState()
        goToMyChat()
        TextSecurePreferences.setLinkPreviewsEnabled(InstrumentationRegistry.getInstrumentation().targetContext, true)
        sendMessage("howdy")
        sendMessage("test")
        // tests url rewriter doesn't crash
        sendMessage("https://www.getsession.org?random_query_parameter=testtesttesttesttesttesttesttest&other_query_parameter=testtesttesttesttesttesttesttest")
        sendMessage("https://www.ámazon.com")
        // TODO: check data / tap URL and check it's displayed properly here
    }

}