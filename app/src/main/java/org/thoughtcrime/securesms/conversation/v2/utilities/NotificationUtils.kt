package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import androidx.appcompat.app.AlertDialog
import network.loki.messenger.R
import org.session.libsession.utilities.recipients.Recipient

object NotificationUtils {
    fun showNotifyDialog(context: Context, thread: Recipient, notifyTypeHandler: (Int)->Unit) {
        val notifyTypes = context.resources.getStringArray(R.array.notify_types)
        val currentSelected = thread.notifyType

        AlertDialog.Builder(context)
            .setSingleChoiceItems(notifyTypes,currentSelected) { d, newSelection ->
                notifyTypeHandler(newSelection)
                d.dismiss()
            }
            .setTitle(R.string.RecipientPreferenceActivity_notification_settings)
            .show()
    }
}