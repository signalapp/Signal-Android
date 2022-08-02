package org.thoughtcrime.securesms.logsubmit

import android.content.Context
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.DistributionId

/**
 * Prints off the current SMS settings
 */

class LogSectionStories : LogSection {
  override fun getTitle(): String = "STORIES"

  override fun getContent(context: Context): CharSequence {
    val myStoryRecord = SignalDatabase.distributionLists.getList(DistributionListId.MY_STORY)
    val myStoryRecipientId = SignalDatabase.distributionLists.getRecipientId(DistributionListId.MY_STORY)
    val output = StringBuilder()

    output.append("--- My Story\n\n")

    if (myStoryRecord != null) {
      output.append("Database ID    : ${myStoryRecord.id}\n")
      output.append("Distribution ID: ${myStoryRecord.distributionId} (Matches expected value? ${myStoryRecord.distributionId == DistributionId.MY_STORY})\n")
      output.append("Recipient ID   : ${presentRecipientId(myStoryRecipientId)}\n")
    } else {
      output.append("< My story does not exist >\n")
    }

    return output
  }

  private fun presentRecipientId(recipientId: RecipientId?): String {
    return recipientId?.serialize() ?: "Not set"
  }
}
