package org.thoughtcrime.securesms.search

import android.content.Intent
import android.os.Bundle
import android.view.View
import org.thoughtcrime.securesms.ContactSelectionActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Optional
import java.util.function.Consumer

/**
 * A single-select contact picker that returns immediately when a contact is tapped.
 */
class SingleContactSelectionActivity : ContactSelectionActivity() {
  override fun onCreate(icicle: Bundle?, ready: Boolean) {
    super.onCreate(icicle, ready)
    toolbar.setNavigationIcon(R.drawable.ic_arrow_left_24)
    toolbar.setNavigationOnClickListener { _: View? ->
      setResult(RESULT_CANCELED, Intent(intent))
      finish()
    }
  }

  override fun onBeforeContactSelected(isFromUnknownSearchKey: Boolean, recipientId: Optional<RecipientId>, number: String?, chatType: Optional<ChatType>, callback: Consumer<Boolean>) {
    callback.accept(true)
    if (recipientId.isPresent) {
      val result = Intent(intent)
      result.putParcelableArrayListExtra(KEY_SELECTED_RECIPIENT, ArrayList(mutableListOf(recipientId.get())))
      setResult(RESULT_OK, result)
      finish()
    }
  }

  override fun onSelectionChanged() {
  }

  companion object {
    const val KEY_SELECTED_RECIPIENT: String = "selected_recipient"
  }
}
