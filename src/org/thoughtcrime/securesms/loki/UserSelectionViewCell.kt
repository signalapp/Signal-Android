package org.thoughtcrime.securesms.loki

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.cell_user_selection_view.view.*
import network.loki.messenger.R
import nl.komponents.kovenant.combine.Tuple2
import org.whispersystems.signalservice.loki.api.LokiGroupChatAPI

class UserSelectionViewCell(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr) {
    var user = Tuple2("", "")
        set(newValue) { field = newValue; update() }
    var hasGroupContext = false

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    companion object {

        fun inflate(layoutInflater: LayoutInflater, parent: ViewGroup): UserSelectionViewCell {
            return layoutInflater.inflate(R.layout.cell_user_selection_view, parent, false) as UserSelectionViewCell
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
        displayNameTextView.text = user.second
        profilePictureImageView.update(user.first)
        val isUserModerator = LokiGroupChatAPI.isUserModerator(user.first, LokiGroupChatAPI.publicChatServerID, LokiGroupChatAPI.publicChatServer)
        moderatorIconImageView.visibility = if (isUserModerator && hasGroupContext) View.VISIBLE else View.GONE
    }
}