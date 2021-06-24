package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import kotlinx.android.synthetic.main.view_link_preview.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.mms.ImageSlide

class LinkPreviewView : LinearLayout {

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_link_preview, this)
    }
    // endregion

    // region Updating
    fun bind(message: MmsMessageRecord, glide: GlideRequests, background: Drawable) {
        mainLinkPreviewContainer.background = background
        mainLinkPreviewContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
        mainLinkPreviewContainer.clipToOutline = true
        // Thumbnail
        val linkPreview = message.linkPreviews.first()
        if (linkPreview.getThumbnail().isPresent) {
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
        val bodyTextView = VisibleMessageContentView.getBodyTextView(context, message)
        mainLinkPreviewContainer.addView(bodyTextView)
    }

    fun recycle() {
        // TODO: Implement
    }
    // endregion
}