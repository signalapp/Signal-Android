package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.OpenGroupUrlParser
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.loki.api.OpenGroupManager
import org.thoughtcrime.securesms.loki.protocol.MultiDeviceProtocol
import org.thoughtcrime.securesms.loki.utilities.OpenGroupUtilities

class OpenGroupInvitationView : FrameLayout {
    private val joinButton: ImageView
    private val openGroupIconContainer: RelativeLayout
    private val openGroupIconImageView: ImageView
    private val nameTextView: TextView
    private val urlTextView: TextView
    private var url: String = ""

    constructor(context: Context): this(context, null)

    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr) {
        View.inflate(context, R.layout.open_group_invitation_view, this)
        joinButton = findViewById(R.id.join_open_group_button)
        openGroupIconContainer = findViewById(R.id.open_group_icon_image_view_container)
        openGroupIconImageView = findViewById(R.id.open_group_icon_image_view)
        nameTextView = findViewById(R.id.name_text_view)
        urlTextView = findViewById(R.id.url_text_view)
        joinButton.setOnClickListener { joinOpenGroup(url) }
    }

    fun setOpenGroup(name: String, url: String, isOutgoing: Boolean = false) {
        nameTextView.text = name
        urlTextView.text = OpenGroupUrlParser.trimQueryParameter(url)
        this.url = url
        joinButton.visibility = if (isOutgoing) View.GONE else View.VISIBLE
        openGroupIconContainer.visibility = if (isOutgoing) View.VISIBLE else View.GONE
    }

    private fun joinOpenGroup(url: String) {
        val openGroup = OpenGroupUrlParser.parseUrl(url)
        val builder = AlertDialog.Builder(context)
        builder.setTitle(context.getString(R.string.ConversationActivity_join_open_group, nameTextView.text.toString()))
        builder.setCancelable(true)
        val message: String =
            context.getString(R.string.ConversationActivity_join_open_group_confirmation_message, nameTextView.text.toString())
        builder.setMessage(message)
        builder.setPositiveButton(R.string.yes) { dialog, _ ->
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    dialog.dismiss()
                    OpenGroupManager.add(openGroup.server, openGroup.room, openGroup.serverPublicKey, context)
                    MultiDeviceProtocol.forceSyncConfigurationNowIfNeeded(context)
                } catch (e: Exception) {
                    Log.e("Loki", "Failed to join open group.", e)
                    Toast.makeText(context, R.string.activity_join_public_chat_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.setNegativeButton(R.string.no, null)
        builder.show()
    }
}