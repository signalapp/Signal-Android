package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import network.loki.messenger.R
import org.session.libsignal.utilities.logging.Log
import java.io.IOException

class OpenGroupInvitationView : FrameLayout {

    companion object {
        private const val TAG = "OpenGroupInvitationView"
    }

    private val joinButton: ImageView
    private val openGroupIcon: ImageView
    private val groupName: TextView
    private val groupUrl: TextView

    constructor(context: Context): this(context, null)

    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr) {
        View.inflate(context, R.layout.open_group_invitation_view, this)
        joinButton = findViewById(R.id.join_open_group)
        openGroupIcon = findViewById(R.id.open_group_icon)
        groupName = findViewById(R.id.group_name)
        groupUrl = findViewById(R.id.group_url)

        joinButton.setOnClickListener {  }
    }

    fun setOpenGroup(name: String, url: String, isOutgoing: Boolean = false) {
        groupName.text = name
        groupUrl.text = url

        if(isOutgoing) {
            joinButton.visibility = View.GONE
            openGroupIcon.visibility = View.VISIBLE
        } else {
            joinButton.visibility = View.VISIBLE
            openGroupIcon.visibility = View.GONE
        }
    }

    fun joinPublicGroup(url: String) {

    }

}