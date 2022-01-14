package org.thoughtcrime.securesms.conversation.v2.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import network.loki.messenger.databinding.ViewLinkPreviewDraftBinding
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.util.toPx

class LinkPreviewDraftView : LinearLayout {
    private lateinit var binding: ViewLinkPreviewDraftBinding
    var delegate: LinkPreviewDraftViewDelegate? = null

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        // Start out with the loader showing and the content view hidden
        binding = ViewLinkPreviewDraftBinding.inflate(LayoutInflater.from(context), this, true)
        binding.linkPreviewDraftContainer.isVisible = false
        binding.thumbnailImageView.clipToOutline = true
        binding.linkPreviewDraftCancelButton.setOnClickListener { cancel() }
    }

    fun update(glide: GlideRequests, linkPreview: LinkPreview) {
        // Hide the loader and show the content view
        binding.linkPreviewDraftContainer.isVisible = true
        binding.linkPreviewDraftLoader.isVisible = false
        binding.thumbnailImageView.radius = toPx(4, resources)
        if (linkPreview.getThumbnail().isPresent) {
            // This internally fetches the thumbnail
            binding.thumbnailImageView.setImageResource(glide, ImageSlide(context, linkPreview.getThumbnail().get()), false, false)
        }
        binding.linkPreviewDraftTitleTextView.text = linkPreview.title
    }

    private fun cancel() {
        delegate?.cancelLinkPreviewDraft()
    }
}

interface LinkPreviewDraftViewDelegate {

    fun cancelLinkPreviewDraft()
}