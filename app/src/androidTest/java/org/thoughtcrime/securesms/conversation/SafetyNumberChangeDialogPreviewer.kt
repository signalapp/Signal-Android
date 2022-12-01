package org.thoughtcrime.securesms.conversation

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.testing.SignalActivityRule

/**
 * Android test to help show SNC dialog quickly with custom data to make sure it displays properly.
 */
@Ignore("For testing/previewing manually, no assertions")
@RunWith(AndroidJUnit4::class)
class SafetyNumberChangeDialogPreviewer {

  @get:Rule val harness = SignalActivityRule(othersCount = 10)

  @Test
  fun testShowLongName() {
    val other: Recipient = Recipient.resolved(harness.others.first())

    SignalDatabase.recipients.setProfileName(other.id, ProfileName.fromParts("Super really long name like omg", "But seriously it's long like really really long"))

    harness.setVerified(other, IdentityTable.VerifiedStatus.VERIFIED)
    harness.changeIdentityKey(other)

    val scenario: ActivityScenario<ConversationActivity> = harness.launchActivity { putExtra("recipient_id", other.id.serialize()) }
    scenario.onActivity {
      SafetyNumberBottomSheet.forRecipientId(other.id).show(it.supportFragmentManager)
    }

    // Uncomment to make dialog stay on screen, otherwise will show/dismiss immediately
    // ThreadUtil.sleep(15000)
  }

  @Test
  fun testShowLargeSheet() {
    SignalDatabase.distributionLists.setPrivacyMode(DistributionListId.MY_STORY, DistributionListPrivacyMode.ONLY_WITH)

    val othersRecipients = harness.others.map { Recipient.resolved(it) }
    othersRecipients.forEach { other ->
      SignalDatabase.recipients.setProfileName(other.id, ProfileName.fromParts("My", "Name"))

      harness.setVerified(other, IdentityTable.VerifiedStatus.DEFAULT)
      harness.changeIdentityKey(other)

      SignalDatabase.distributionLists.addMemberToList(DistributionListId.MY_STORY, DistributionListPrivacyMode.ONLY_WITH, other.id)
    }

    val myStoryRecipientId = SignalDatabase.distributionLists.getRecipientId(DistributionListId.MY_STORY)!!
    val scenario: ActivityScenario<ConversationActivity> = harness.launchActivity { putExtra("recipient_id", harness.others.first().serialize()) }
    scenario.onActivity { conversationActivity ->
      SafetyNumberBottomSheet
        .forIdentityRecordsAndDestinations(
          identityRecords = ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecords(othersRecipients).identityRecords,
          destinations = listOf(ContactSearchKey.RecipientSearchKey.Story(myStoryRecipientId))
        )
        .show(conversationActivity.supportFragmentManager)
    }

    // Uncomment to make dialog stay on screen, otherwise will show/dismiss immediately
    // ThreadUtil.sleep( 30000)
  }
}
