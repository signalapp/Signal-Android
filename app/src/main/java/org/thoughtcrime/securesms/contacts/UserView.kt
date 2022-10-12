package org.thoughtcrime.securesms.contacts

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewUserBinding
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionManagerUtilities
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideRequests

class UserView : LinearLayout {
    private lateinit var binding: ViewUserBinding
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
        binding = ViewUserBinding.inflate(LayoutInflater.from(context), this, true)
    }
    // endregion

    // region Updating
    fun bind(user: Recipient, glide: GlideRequests, actionIndicator: ActionIndicator, isSelected: Boolean = false) {
        fun getUserDisplayName(publicKey: String): String {
            val contact = DatabaseComponent.get(context).sessionContactDatabase().getContactWithSessionID(publicKey)
            return contact?.displayName(Contact.ContactContext.REGULAR) ?: publicKey
        }
        val threadID = DatabaseComponent.get(context).threadDatabase().getOrCreateThreadIdFor(user)
        MentionManagerUtilities.populateUserPublicKeyCacheIfNeeded(threadID, context) // FIXME: This is a bad place to do this
        val address = user.address.serialize()
        binding.profilePictureView.root.glide = glide
        binding.profilePictureView.root.update(user)
        binding.actionIndicatorImageView.setImageResource(R.drawable.ic_baseline_edit_24)
        binding.nameTextView.text = if (user.isGroupRecipient) user.name else getUserDisplayName(address)
        when (actionIndicator) {
            ActionIndicator.None -> {
                binding.actionIndicatorImageView.visibility = View.GONE
            }
            ActionIndicator.Menu -> {
                binding.actionIndicatorImageView.visibility = View.VISIBLE
                binding.actionIndicatorImageView.setImageResource(R.drawable.ic_more_horiz_white)
            }
            ActionIndicator.Tick -> {
                binding.actionIndicatorImageView.visibility = View.VISIBLE
                if (isSelected) {
                    binding.actionIndicatorImageView.setImageResource(R.drawable.padded_circle_accent)
                } else {
                    binding.actionIndicatorImageView.setImageDrawable(null)
                }
            }
        }
    }

    fun toggleCheckbox(isSelected: Boolean = false) {
        binding.actionIndicatorImageView.visibility = View.VISIBLE
        if (isSelected) {
            binding.actionIndicatorImageView.setImageResource(R.drawable.padded_circle_accent)
        } else {
            binding.actionIndicatorImageView.setImageDrawable(null)
        }
    }

    fun unbind() {
        binding.profilePictureView.root.recycle()
    }
    // endregion
}
