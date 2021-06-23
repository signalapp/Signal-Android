package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import kotlinx.android.synthetic.main.view_link_preview.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities
import org.thoughtcrime.securesms.mms.GlideRequests

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
        // TODO: Handle downloading state
        val uri = linkPreview.thumbnail.get().dataUri ?: return
        glide.load(uri).into(thumbnailImageView)
        // TODO: Properly use glide and the actual thumbnail
        // Title
        titleTextView.text = linkPreview.title
        val textColorID = if (message.isOutgoing && UiModeUtilities.isDayUiMode(context)) {
            R.color.white
        } else {
            if (UiModeUtilities.isDayUiMode(context)) R.color.black else R.color.white
        }
        titleTextView.setTextColor(ResourcesCompat.getColor(resources, textColorID, context.theme))
        // Body
        mainLinkPreviewContainer.addView(VisibleMessageContentView.getBodyTextView(context, message))
    }

    fun recycle() {
        // TODO: Implement
    }
    // endregion
}