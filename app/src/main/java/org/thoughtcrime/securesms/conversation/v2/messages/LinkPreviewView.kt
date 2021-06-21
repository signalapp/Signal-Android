package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_link_preview.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
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
    fun bind(message: MmsMessageRecord, glide: GlideRequests, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean) {
        mainLinkPreviewContainer.outlineProvider = getOutlineProvider(message.isOutgoing, isStartOfMessageCluster, isEndOfMessageCluster)
        mainLinkPreviewContainer.clipToOutline = true
        // Thumbnail
        val linkPreview = message.linkPreviews.first()
        // TODO: Handle downloading state
        val uri = linkPreview.thumbnail.get().dataUri!!
        glide.load(uri).into(thumbnailImageView)
        // TODO: Properly use glide and the actual thumbnail
        // Title
        titleTextView.text = linkPreview.title
    }

    fun recycle() {
        // TODO: Implement
    }
    // endregion

    // region Convenience
    private fun getOutlineProvider(isOutgoing: Boolean, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean): ViewOutlineProvider {
        return object : ViewOutlineProvider() {

            override fun getOutline(view: View, outline: Outline) {
                val path = Path()
                val rect = RectF(0.0f, 0.0f, view.width.toFloat(), view.height.toFloat())
                val topLeft = if (isOutgoing) {
                    resources.getDimension(R.dimen.message_corner_radius)
                } else {
                    if (isStartOfMessageCluster) resources.getDimension(R.dimen.message_corner_radius) else resources.getDimension(R.dimen.message_corner_collapse_radius)
                }
                val topRight = if (isOutgoing) {
                    if (isStartOfMessageCluster) resources.getDimension(R.dimen.message_corner_radius) else resources.getDimension(R.dimen.message_corner_collapse_radius)
                } else {
                    resources.getDimension(R.dimen.message_corner_radius)
                }
                path.addRoundRect(rect, floatArrayOf( topLeft, topLeft, topRight, topRight, 0.0f, 0.0f, 0.0f, 0.0f ), Path.Direction.CW)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    outline.setPath(path)
                } else {
                    @Suppress("DEPRECATION") outline.setConvexPath(path)
                }
            }
        }
    }
    // endregion
}