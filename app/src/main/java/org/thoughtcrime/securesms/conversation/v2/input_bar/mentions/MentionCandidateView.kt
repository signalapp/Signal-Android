package org.thoughtcrime.securesms.conversation.v2.input_bar.mentions

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import kotlinx.android.synthetic.main.view_mention_candidate.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.mentions.Mention
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.thoughtcrime.securesms.mms.GlideRequests

class MentionCandidateView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : RelativeLayout(context, attrs, defStyleAttr) {
    var candidate = Mention("", "")
        set(newValue) { field = newValue; update() }
    var glide: GlideRequests? = null
    var openGroupServer: String? = null
    var openGroupRoom: String? = null

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    companion object {

        fun inflate(layoutInflater: LayoutInflater, parent: ViewGroup): MentionCandidateView {
            return layoutInflater.inflate(R.layout.view_mention_candidate_v2, parent, false) as MentionCandidateView
        }
    }

    private fun update() {
        mentionCandidateNameTextView.text = candidate.displayName
        profilePictureView.publicKey = candidate.publicKey
        profilePictureView.displayName = candidate.displayName
        profilePictureView.additionalPublicKey = null
        profilePictureView.glide = glide!!
        profilePictureView.update()
        if (openGroupServer != null && openGroupRoom != null) {
            val isUserModerator = OpenGroupAPIV2.isUserModerator(candidate.publicKey, openGroupRoom!!, openGroupServer!!)
            moderatorIconImageView.visibility = if (isUserModerator) View.VISIBLE else View.GONE
        } else {
            moderatorIconImageView.visibility = View.GONE
        }
    }
}