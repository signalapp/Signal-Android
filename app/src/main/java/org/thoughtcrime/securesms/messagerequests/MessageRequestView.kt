package org.thoughtcrime.securesms.messagerequests

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewMessageRequestBinding
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities.highlightMentions
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale

class MessageRequestView : LinearLayout {
    private lateinit var binding: ViewMessageRequestBinding
    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    var thread: ThreadRecord? = null

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewMessageRequestBinding.inflate(LayoutInflater.from(context), this, true)
        layoutParams = RecyclerView.LayoutParams(screenWidth, RecyclerView.LayoutParams.WRAP_CONTENT)
    }
    // endregion

    // region Updating
    fun bind(thread: ThreadRecord, glide: GlideRequests) {
        this.thread = thread
        binding.profilePictureView.glide = glide
        val senderDisplayName = getUserDisplayName(thread.recipient)
            ?: thread.recipient.address.toString()
        binding.displayNameTextView.text = senderDisplayName
        binding.timestampTextView.text = DateUtils.getDisplayFormattedTimeSpanString(context, Locale.getDefault(), thread.date)
        val rawSnippet = thread.getDisplayBody(context)
        val snippet = highlightMentions(rawSnippet, thread.threadId, context)
        binding.snippetTextView.text = snippet

        post {
            binding.profilePictureView.update(thread.recipient)
        }
    }

    fun recycle() {
        binding.profilePictureView.recycle()
    }

    private fun getUserDisplayName(recipient: Recipient): String? {
        return if (recipient.isLocalNumber) {
            context.getString(R.string.note_to_self)
        } else {
            recipient.name // Internally uses the Contact API
        }
    }
    // endregion
}
