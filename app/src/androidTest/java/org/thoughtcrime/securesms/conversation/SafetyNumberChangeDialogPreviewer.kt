package org.thoughtcrime.securesms.conversation

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.conversation.ui.error.SafetyNumberChangeDialog
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.SignalActivityRule

/**
 * Android test to help show SNC dialog quickly with custom data to make sure it displays properly.
 */
@RunWith(AndroidJUnit4::class)
class SafetyNumberChangeDialogPreviewer {

  @get:Rule val harness = SignalActivityRule()

  @Test
  fun testShowLongName() {
    val other: Recipient = Recipient.resolved(harness.others.first())

    SignalDatabase.recipients.setProfileName(other.id, ProfileName.fromParts("Super really long name like omg", "But seriously it's long like really really long"))

    harness.setVerified(other, IdentityDatabase.VerifiedStatus.VERIFIED)
    harness.changeIdentityKey(other)

    val scenario: ActivityScenario<ConversationActivity> = harness.launchActivity { putExtra("recipient_id", other.id.serialize()) }
    scenario.onActivity {
      SafetyNumberChangeDialog.show(it.supportFragmentManager, other.id)
    }

    // Uncomment to make dialog stay on screen, otherwise will show/dismiss immediately
    // ThreadUtil.sleep(15000)
  }
}
