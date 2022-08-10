package org.thoughtcrime.securesms.conversation.v2.input_bar.mentions

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import network.loki.messenger.databinding.ViewMentionCandidateV2Binding
import org.session.libsession.messaging.mentions.Mention
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.mms.GlideRequests

class MentionCandidateView : RelativeLayout {
    private lateinit var binding: ViewMentionCandidateV2Binding
    var candidate = Mention("", "")
        set(newValue) { field = newValue; update() }
    var glide: GlideRequests? = null
    var openGroupServer: String? = null
    var openGroupRoom: String? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewMentionCandidateV2Binding.inflate(LayoutInflater.from(context), this, true)
    }

    private fun update() = with(binding) {
        mentionCandidateNameTextView.text = candidate.displayName
        profilePictureView.root.publicKey = candidate.publicKey
        profilePictureView.root.displayName = candidate.displayName
        profilePictureView.root.additionalPublicKey = null
        profilePictureView.root.glide = glide!!
        profilePictureView.root.update()
        if (openGroupServer != null && openGroupRoom != null) {
            val isUserModerator = OpenGroupManager.isUserModerator(context, "$openGroupRoom.$openGroupServer", candidate.publicKey)
            moderatorIconImageView.visibility = if (isUserModerator) View.VISIBLE else View.GONE
        } else {
            moderatorIconImageView.visibility = View.GONE
        }
    }
}