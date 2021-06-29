package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import kotlinx.android.synthetic.main.view_link_preview.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.components.CornerMask
import org.thoughtcrime.securesms.conversation.v2.dialogs.OpenURLDialog
import org.thoughtcrime.securesms.conversation.v2.utilities.MessageBubbleUtilities
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.mms.ImageSlide

class LinkPreviewView : LinearLayout {
    private val cornerMask by lazy { CornerMask(this) }
    private var url: String? = null

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_link_preview, this)
    }
    // endregion

    // region Updating
    fun bind(message: MmsMessageRecord, glide: GlideRequests, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean, searchQuery: String?) {
        val linkPreview = message.linkPreviews.first()
        url = linkPreview.url
        // Thumbnail
        if (linkPreview.getThumbnail().isPresent) {
            // This internally fetches the thumbnail
            thumbnailImageView.setImageResource(glide, ImageSlide(context, linkPreview.getThumbnail().get()), false, false)
        }
        // Title
        titleTextView.text = linkPreview.title
        val textColorID = if (message.isOutgoing && UiModeUtilities.isDayUiMode(context)) {
            R.color.white
        } else {
            if (UiModeUtilities.isDayUiMode(context)) R.color.black else R.color.white
        }
        titleTextView.setTextColor(ResourcesCompat.getColor(resources, textColorID, context.theme))
        // Body
        val bodyTextView = VisibleMessageContentView.getBodyTextView(context, message, searchQuery)
        mainLinkPreviewContainer.addView(bodyTextView)
        // Corner radii
        val cornerRadii = MessageBubbleUtilities.calculateRadii(context, isStartOfMessageCluster, isEndOfMessageCluster, message.isOutgoing)
        cornerMask.setTopLeftRadius(cornerRadii[0])
        cornerMask.setTopRightRadius(cornerRadii[1])
        cornerMask.setBottomRightRadius(cornerRadii[2])
        cornerMask.setBottomLeftRadius(cornerRadii[3])
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        cornerMask.mask(canvas)
    }
    // endregion

    // region Interaction
    fun openURL() {
        val url = this.url ?: return
        val activity = context as AppCompatActivity
        OpenURLDialog(url).show(activity.supportFragmentManager, "Open URL Dialog")
    }
    // endregion
}