package org.thoughtcrime.securesms.conversation.v2.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.view_link_preview_draft.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.mms.ImageSlide

class LinkPreviewDraftView : LinearLayout {
    var delegate: LinkPreviewDraftViewDelegate? = null

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        // Start out with the loader showing and the content view hidden
        LayoutInflater.from(context).inflate(R.layout.view_link_preview_draft, this)
        linkPreviewDraftContainer.isVisible = false
        thumbnailImageView.clipToOutline = true
        linkPreviewDraftCancelButton.setOnClickListener { cancel() }
    }

    fun update(glide: GlideRequests, linkPreview: LinkPreview) {
        // Hide the loader and show the content view
        linkPreviewDraftContainer.isVisible = true
        linkPreviewDraftLoader.isVisible = false
        thumbnailImageView.radius = toPx(4, resources)
        if (linkPreview.getThumbnail().isPresent) {
            // This internally fetches the thumbnail
            thumbnailImageView.setImageResource(glide, ImageSlide(context, linkPreview.getThumbnail().get()), false, false)
        }
        linkPreviewDraftTitleTextView.text = linkPreview.title
    }

    private fun cancel() {
        delegate?.cancelLinkPreviewDraft()
    }
}

interface LinkPreviewDraftViewDelegate {

    fun cancelLinkPreviewDraft()
}