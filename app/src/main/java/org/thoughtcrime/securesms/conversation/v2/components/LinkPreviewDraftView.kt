package org.thoughtcrime.securesms.conversation.v2.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.view_link_preview_draft.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.mms.ImageSlide

class LinkPreviewDraftView : LinearLayout {
    var delegate: LinkPreviewDraftViewDelegate? = null

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_link_preview_draft, this)
        linkPreviewDraftContainer.isVisible = false
        thumbnailImageView.clipToOutline = true
        linkPreviewDraftCancelButton.setOnClickListener { cancel() }
    }
    // endregion

    // region Updating
    fun update(glide: GlideRequests, linkPreview: LinkPreview) {
        linkPreviewDraftContainer.isVisible = true
        linkPreviewDraftLoader.isVisible = false
        if (linkPreview.getThumbnail().isPresent) {
            thumbnailImageView.setImageResource(glide, ImageSlide(context, linkPreview.getThumbnail().get()), false, false)
        }
        linkPreviewDraftTitleTextView.text = linkPreview.title
    }
    // endregion

    // region Interaction
    private fun cancel() {
        delegate?.cancelLinkPreviewDraft()
    }
    // endregion
}

interface LinkPreviewDraftViewDelegate {

    fun cancelLinkPreviewDraft()
}