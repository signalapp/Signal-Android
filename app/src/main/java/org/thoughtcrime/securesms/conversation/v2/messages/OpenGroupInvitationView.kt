package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewOpenGroupInvitationBinding
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.utilities.OpenGroupUrlParser
import org.thoughtcrime.securesms.conversation.v2.dialogs.JoinOpenGroupDialog
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.getAccentColor

class OpenGroupInvitationView : LinearLayout {
    private val binding: ViewOpenGroupInvitationBinding by lazy { ViewOpenGroupInvitationBinding.bind(this) }
    private var data: UpdateMessageData.Kind.OpenGroupInvitation? = null

    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    fun bind(message: MessageRecord, @ColorInt textColor: Int) {
        // FIXME: This is a really weird approach...
        val umd = UpdateMessageData.fromJSON(message.body)!!
        val data = umd.kind as UpdateMessageData.Kind.OpenGroupInvitation
        this.data = data
        val iconID = if (message.isOutgoing) R.drawable.ic_globe else R.drawable.ic_plus
        val backgroundColor = if (!message.isOutgoing) context.getAccentColor()
        else ContextCompat.getColor(context, R.color.transparent_black_6)
        with(binding){
            openGroupInvitationIconImageView.setImageResource(iconID)
            openGroupInvitationIconBackground.backgroundTintList = ColorStateList.valueOf(backgroundColor)
            openGroupTitleTextView.text = data.groupName
            openGroupURLTextView.text = OpenGroupUrlParser.trimQueryParameter(data.groupUrl)
            openGroupTitleTextView.setTextColor(textColor)
            openGroupJoinMessageTextView.setTextColor(textColor)
            openGroupURLTextView.setTextColor(textColor)
        }
    }

    fun joinOpenGroup() {
        val data = data ?: return
        val activity = context as AppCompatActivity
        JoinOpenGroupDialog(data.groupName, data.groupUrl).show(activity.supportFragmentManager, "Join Open Group Dialog")
    }
}