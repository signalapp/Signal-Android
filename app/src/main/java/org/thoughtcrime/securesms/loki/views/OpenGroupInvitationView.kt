package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import network.loki.messenger.R
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.OpenGroupUrlParser
import org.session.libsignal.utilities.logging.Log
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol
import org.thoughtcrime.securesms.loki.utilities.OpenGroupUtilities

class OpenGroupInvitationView : FrameLayout {

    companion object {
        private const val TAG = "OpenGroupInvitationView"
    }

    private val joinButton: ImageView
    private val openGroupIcon: ImageView
    private val groupName: TextView
    private val displayedUrl: TextView

    private var groupUrl: String = ""

    constructor(context: Context): this(context, null)

    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr) {
        View.inflate(context, R.layout.open_group_invitation_view, this)
        joinButton = findViewById(R.id.join_open_group)
        openGroupIcon = findViewById(R.id.open_group_icon)
        groupName = findViewById(R.id.group_name)
        displayedUrl = findViewById(R.id.group_url)

        joinButton.setOnClickListener { joinPublicGroup(groupUrl) }
    }

    fun setOpenGroup(name: String, url: String, isOutgoing: Boolean = false) {
        groupName.text = name
        displayedUrl.text = OpenGroupUrlParser.trimParameter(url)
        groupUrl = url

        if(isOutgoing) {
            joinButton.visibility = View.GONE
            openGroupIcon.visibility = View.VISIBLE
        } else {
            joinButton.visibility = View.VISIBLE
            openGroupIcon.visibility = View.GONE
        }
    }

    private fun joinPublicGroup(url: String) {
        val openGroup = OpenGroupUrlParser.parseUrl(url)
        val builder = AlertDialog.Builder(context)
        builder.setTitle(context.getString(R.string.ConversationActivity_join_open_group, groupName.text.toString()))
        builder.setIconAttribute(R.attr.dialog_info_icon)
        builder.setCancelable(true)

        var message: String =
            context.getString(R.string.ConversationActivity_join_open_group_confirmation_message, groupName.text.toString())

        builder.setMessage(message)
        builder.setPositiveButton(R.string.yes) { dialog, which ->
            try {
                val group = OpenGroupUtilities.addGroup(context, openGroup.server, openGroup.room, openGroup.serverPublicKey)
                val threadID = GroupManager.getOpenGroupThreadID(group.id, context)
                val groupID = GroupUtil.getEncodedOpenGroupID(group.id.toByteArray())

                MultiDeviceProtocol.forceSyncConfigurationNowIfNeeded(context)
            } catch (e: Exception) {
                Log.e("JoinPublicChatActivity", "Failed to join open group.", e)
                Toast.makeText(context, R.string.activity_join_public_chat_error, Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton(R.string.no, null)
        builder.show()
    }

}