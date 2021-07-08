package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.dialog_join_open_group.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.open_groups.OpenGroupV2
import org.session.libsession.utilities.OpenGroupUrlParser
import org.session.libsignal.utilities.ThreadUtils
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog
import org.thoughtcrime.securesms.loki.api.OpenGroupManager
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol

/** Shown upon tapping an open group invitation. */
class JoinOpenGroupDialog(private val name: String, private val url: String) : BaseDialog() {

    override fun setContentView(builder: AlertDialog.Builder) {
        val contentView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_join_open_group, null)
        val title = resources.getString(R.string.dialog_join_open_group_title, name)
        contentView.joinOpenGroupTitleTextView.text = title
        val explanation = resources.getString(R.string.dialog_join_open_group_explanation, name)
        val spannable = SpannableStringBuilder(explanation)
        val startIndex = explanation.indexOf(name)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + name.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        contentView.joinOpenGroupExplanationTextView.text = spannable
        contentView.cancelButton.setOnClickListener { dismiss() }
        contentView.joinButton.setOnClickListener { join() }
        builder.setView(contentView)
    }

    private fun join() {
        val openGroup = OpenGroupUrlParser.parseUrl(url)
        val activity = requireContext() as AppCompatActivity
        ThreadUtils.queue {
            OpenGroupManager.add(openGroup.server, openGroup.room, openGroup.serverPublicKey, activity)
            MultiDeviceProtocol.forceSyncConfigurationNowIfNeeded(activity)
        }
        dismiss()
    }
}