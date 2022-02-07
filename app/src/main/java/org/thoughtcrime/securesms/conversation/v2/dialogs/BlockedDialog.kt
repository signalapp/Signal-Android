package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import network.loki.messenger.R
import network.loki.messenger.databinding.DialogBlockedBinding
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog
import org.thoughtcrime.securesms.dependencies.DatabaseComponent

/** Shown upon sending a message to a user that's blocked. */
class BlockedDialog(private val recipient: Recipient) : BaseDialog() {

    override fun setContentView(builder: AlertDialog.Builder) {
        val binding = DialogBlockedBinding.inflate(LayoutInflater.from(requireContext()))
        val contactDB = DatabaseComponent.get(requireContext()).sessionContactDatabase()
        val sessionID = recipient.address.toString()
        val contact = contactDB.getContactWithSessionID(sessionID)
        val name = contact?.displayName(Contact.ContactContext.REGULAR) ?: sessionID
        val title = resources.getString(R.string.dialog_blocked_title, name)
        binding.blockedTitleTextView.text = title
        val explanation = resources.getString(R.string.dialog_blocked_explanation, name)
        val spannable = SpannableStringBuilder(explanation)
        val startIndex = explanation.indexOf(name)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + name.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.blockedExplanationTextView.text = spannable
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.unblockButton.setOnClickListener { unblock() }
        builder.setView(binding.root)
    }

    private fun unblock() {
        DatabaseComponent.get(requireContext()).recipientDatabase().setBlocked(recipient, false)
        dismiss()
    }
}