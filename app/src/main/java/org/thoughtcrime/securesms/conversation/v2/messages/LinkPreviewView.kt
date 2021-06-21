package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import kotlinx.android.synthetic.main.view_link_preview.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri
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
    fun bind(message: MmsMessageRecord, glide: GlideRequests) {
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
}