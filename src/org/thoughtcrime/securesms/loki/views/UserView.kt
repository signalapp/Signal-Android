package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_conversation.view.profilePictureView
import kotlinx.android.synthetic.main.view_user.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.loki.protocol.mentions.MentionsManager

class UserView : LinearLayout {

    // region Lifecycle
    constructor(context: Context) : super(context) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        setUpViewHierarchy()
    }

    private fun setUpViewHierarchy() {
        val inflater = context.applicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.view_user, null)
        addView(contentView)
    }
    // endregion

    // region Updating
    fun setCheckBoxVisible(visible: Boolean) {
        tickImageView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun bind(user: Recipient, isSelected: Boolean, glide: GlideRequests, isEditingGroup: Boolean) {
        val address = user.address.serialize()
        if (user.isGroupRecipient) {
            if ("Session Public Chat" == user.name || user.address.isRSSFeed) {
                profilePictureView.hexEncodedPublicKey = ""
                profilePictureView.additionalHexEncodedPublicKey = null
                profilePictureView.isRSSFeed = true
            } else {
                val threadID = GroupManager.getThreadIDFromGroupID(address, context)
                val users = MentionsManager.shared.userPublicKeyCache[threadID]?.toList()
                        ?: listOf()
                val randomUsers = users.sorted() // Sort to provide a level of stability
                profilePictureView.hexEncodedPublicKey = randomUsers.getOrNull(0) ?: ""
                profilePictureView.additionalHexEncodedPublicKey = randomUsers.getOrNull(1) ?: ""
                profilePictureView.isRSSFeed = false

            }
        } else {
            profilePictureView.hexEncodedPublicKey = address
            profilePictureView.additionalHexEncodedPublicKey = null
            profilePictureView.isRSSFeed = false
        }
        tickImageView.setImageResource(R.drawable.ic_edit_white_24dp)
        profilePictureView.glide = glide
        profilePictureView.update()
        nameTextView.text = user.name ?: "Unknown Contact"
        if (isEditingGroup) {
            tickImageView.setImageResource(R.drawable.ic_more_horiz_white)
        } else {
            tickImageView.setImageResource(if (isSelected) R.drawable.ic_circle_check else R.drawable.ic_circle)
        }
    }
    // endregion
}