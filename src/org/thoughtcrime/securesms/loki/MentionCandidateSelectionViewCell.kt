package org.thoughtcrime.securesms.loki

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.cell_mention_candidate_selection_view.view.*
import network.loki.messenger.R
import org.whispersystems.signalservice.loki.api.LokiPublicChatAPI
import org.whispersystems.signalservice.loki.messaging.Mention

class MentionCandidateSelectionViewCell(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr) {
    var mentionCandidate = Mention("", "")
        set(newValue) { field = newValue; update() }
    var publicChatServer: String? = null
    var publicChatChannel: Long? = null

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    companion object {

        fun inflate(layoutInflater: LayoutInflater, parent: ViewGroup): MentionCandidateSelectionViewCell {
            return layoutInflater.inflate(R.layout.cell_mention_candidate_selection_view, parent, false) as MentionCandidateSelectionViewCell
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        profilePictureImageViewContainer.outlineProvider = object : ViewOutlineProvider() {

            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        profilePictureImageViewContainer.clipToOutline = true
    }

    private fun update() {
        displayNameTextView.text = mentionCandidate.displayName
        profilePictureImageView.update(mentionCandidate.hexEncodedPublicKey)
        if (publicChatServer != null && publicChatChannel != null) {
            val isUserModerator = LokiPublicChatAPI.isUserModerator(mentionCandidate.hexEncodedPublicKey, publicChatChannel!!, publicChatServer!!)
            moderatorIconImageView.visibility = if (isUserModerator) View.VISIBLE else View.GONE
        } else {
            moderatorIconImageView.visibility = View.GONE
        }
    }
}