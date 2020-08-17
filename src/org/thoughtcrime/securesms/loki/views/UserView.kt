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
    enum class ActionIndicator {
        NONE,
        MENU,
        CHECK_BOX,
    }

    fun bind(user: Recipient, glide: GlideRequests, actionIndicator: ActionIndicator, isSelected: Boolean = false) {
        val address = user.address.serialize()
        if (user.isGroupRecipient) {
            if ("Session Public Chat" == user.name || user.address.isRSSFeed) {
                profilePictureView.publicKey = ""
                profilePictureView.additionalPublicKey = null
                profilePictureView.isRSSFeed = true
            } else {
                val threadID = GroupManager.getThreadIDFromGroupID(address, context)
                val users = MentionsManager.shared.userPublicKeyCache[threadID]?.toList()
                        ?: listOf()
                val randomUsers = users.sorted() // Sort to provide a level of stability
                profilePictureView.publicKey = randomUsers.getOrNull(0) ?: ""
                profilePictureView.additionalPublicKey = randomUsers.getOrNull(1) ?: ""
                profilePictureView.isRSSFeed = false

            }
        } else {
            profilePictureView.publicKey = address
            profilePictureView.additionalPublicKey = null
            profilePictureView.isRSSFeed = false
        }
        tickImageView.setImageResource(R.drawable.ic_edit_white_24dp)
        profilePictureView.glide = glide
        profilePictureView.update()
        nameTextView.text = user.name ?: "Unknown Contact"

        when (actionIndicator) {
            ActionIndicator.NONE -> {
                tickImageView.visibility = View.GONE
            }
            ActionIndicator.MENU -> {
                tickImageView.visibility = View.VISIBLE
                tickImageView.setImageResource(R.drawable.ic_more_horiz_white)
            }
            ActionIndicator.CHECK_BOX -> {
                tickImageView.visibility = View.VISIBLE
                tickImageView.setImageResource(if (isSelected) R.drawable.ic_circle_check else R.drawable.ic_circle)
            }
        }
    }
    // endregion
}