package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_conversation.view.profilePictureView
import kotlinx.android.synthetic.main.view_user.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.utilities.MentionManagerUtilities
import org.thoughtcrime.securesms.mms.GlideRequests
import org.session.libsession.utilities.recipients.Recipient

class UserView : LinearLayout {
    var openGroupThreadID: Long = -1 // FIXME: This is a bit ugly

    enum class ActionIndicator {
        None,
        Menu,
        Tick
    }

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
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.view_user, null)
        addView(contentView)
    }
    // endregion

    // region Updating
    fun bind(user: Recipient, glide: GlideRequests, actionIndicator: ActionIndicator, isSelected: Boolean = false) {
        fun getUserDisplayName(publicKey: String): String {
            val contact = DatabaseFactory.getSessionContactDatabase(context).getContactWithSessionID(publicKey)
            return contact?.displayName(Contact.ContactContext.REGULAR) ?: publicKey
        }
        val threadID = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(user)
        MentionManagerUtilities.populateUserPublicKeyCacheIfNeeded(threadID, context) // FIXME: This is a bad place to do this
        val address = user.address.serialize()
        profilePictureView.glide = glide
        profilePictureView.update(user, threadID)
        actionIndicatorImageView.setImageResource(R.drawable.ic_baseline_edit_24)
        nameTextView.text = if (user.isGroupRecipient) user.name else getUserDisplayName(address)
        when (actionIndicator) {
            ActionIndicator.None -> {
                actionIndicatorImageView.visibility = View.GONE
            }
            ActionIndicator.Menu -> {
                actionIndicatorImageView.visibility = View.VISIBLE
                actionIndicatorImageView.setImageResource(R.drawable.ic_more_horiz_white)
            }
            ActionIndicator.Tick -> {
                actionIndicatorImageView.visibility = View.VISIBLE
                actionIndicatorImageView.setImageResource(if (isSelected) R.drawable.ic_circle_check else R.drawable.ic_circle)
            }
        }
    }

    fun toggleCheckbox(isSelected: Boolean = false) {
        actionIndicatorImageView.visibility = View.VISIBLE
        actionIndicatorImageView.setImageResource(if (isSelected) R.drawable.ic_circle_check else R.drawable.ic_circle)
    }

    fun unbind() {

    }
    // endregion
}
