package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.dialog_download.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog
import org.thoughtcrime.securesms.database.DatabaseFactory

/** Shown when receiving media from a contact for the first time, to confirm that
 * they are to be trusted and files sent by them are to be downloaded. */
class DownloadDialog(private val recipient: Recipient) : BaseDialog() {

    override fun setContentView(builder: AlertDialog.Builder) {
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_download, null)
        val contactDB = DatabaseFactory.getSessionContactDatabase(requireContext())
        val sessionID = recipient.address.toString()
        val contact = contactDB.getContactWithSessionID(sessionID)
        val name = contact?.displayName(Contact.ContactContext.REGULAR) ?: sessionID
        val title = resources.getString(R.string.dialog_download_title, name)
        contentView.downloadTitleTextView.text = title
        val explanation = resources.getString(R.string.dialog_download_explanation, name)
        val spannable = SpannableStringBuilder(explanation)
        val startIndex = explanation.indexOf(name)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + name.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        contentView.downloadExplanationTextView.text = spannable
        contentView.cancelButton.setOnClickListener { dismiss() }
        contentView.downloadButton.setOnClickListener { trust() }
        builder.setView(contentView)
    }

    private fun trust() {
        // TODO: Implement
    }
}