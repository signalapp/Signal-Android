package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.view.isVisible
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import kotlinx.android.synthetic.main.thumbnail_view.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.utilities.Util.equals
import org.session.libsession.utilities.ViewUtil
import org.thoughtcrime.securesms.components.TransferControlView
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri
import org.thoughtcrime.securesms.mms.GlideRequest
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.SlideClickListener

class ThumbnailView: FrameLayout {

    companion object {
        private const val WIDTH = 0
        private const val HEIGHT = 1
    }

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize(null) }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize(attrs) }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize(attrs) }

    private val image by lazy { thumbnail_image }
    private val playOverlay by lazy { play_overlay }
    private val captionIcon by lazy { thumbnail_caption_icon }
    val loadIndicator: ProgressBar by lazy { thumbnail_load_indicator }
    private val transferControls by lazy { ViewUtil.inflateStub<TransferControlView>(this, R.id.transfer_controls_stub) }

    private val dimensDelegate = ThumbnailDimensDelegate()

    var thumbnailClickListener: SlideClickListener? = null

    private var slide: Slide? = null

    private fun initialize(attrs: AttributeSet?) {
        inflate(context, R.layout.thumbnail_view, this)
        if (attrs != null) {
            val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.ThumbnailView, 0, 0)

            dimensDelegate.setBounds(typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_minWidth, 0),
                    typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_minHeight, 0),
                    typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_maxWidth, 0),
                    typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_maxHeight, 0))

            typedArray.recycle()
        }
    }

    // region Lifecycle
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val adjustedDimens = dimensDelegate.resourceSize()
        if (adjustedDimens[WIDTH] == 0 && adjustedDimens[HEIGHT] == 0) {
            return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        val finalWidth: Int = adjustedDimens[WIDTH] + paddingLeft + paddingRight
        val finalHeight: Int = adjustedDimens[HEIGHT] + paddingTop + paddingBottom

        super.onMeasure(
                MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY)
        )
    }

    private fun getDefaultWidth() = maxOf(layoutParams?.width ?: 0, 0)
    private fun getDefaultHeight() = maxOf(layoutParams?.height ?: 0, 0)

    // endregion

    // region Interaction
    fun setImageResource(glide: GlideRequests, slide: Slide, showControls: Boolean, isPreview: Boolean) {
        return setImageResource(glide, slide, showControls, isPreview, 0, 0)
    }

    fun setImageResource(glide: GlideRequests, slide: Slide,
                         showControls: Boolean, isPreview: Boolean,
                         naturalWidth: Int, naturalHeight: Int) {

        val currentSlide = this.slide

        if (showControls) {
            transferControls.setSlide(slide)
//            transferControls.setDownloadClickListener() TODO: re-add this
        } else {
            transferControls.isVisible = false
        }

        playOverlay.isVisible = (slide.thumbnailUri != null && slide.hasPlayOverlay() &&
                (slide.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_DONE || isPreview))

        if (equals(currentSlide, slide)) {
            // don't re-load slide
            return
        }


        if (currentSlide != null && currentSlide.fastPreflightId != null && currentSlide.fastPreflightId == slide.fastPreflightId) {
            // not reloading slide for fast preflight
            this.slide = slide
        }

        this.slide = slide

        captionIcon.isVisible = slide.caption.isPresent
        loadIndicator.isVisible = slide.isInProgress

        dimensDelegate.setDimens(naturalWidth, naturalHeight)
        invalidate()

        when {
            slide.thumbnailUri != null -> {
                buildThumbnailGlideRequest(glide, slide).into(image)
            }
            slide.hasPlaceholder() -> {
                buildPlaceholderGlideRequest(glide, slide).into(image)
            }
            else -> {
                glide.clear(image)
            }
        }
    }

    fun buildThumbnailGlideRequest(glide: GlideRequests, slide: Slide): GlideRequest<Drawable> {

        val dimens = dimensDelegate.resourceSize()

        val request = glide.load(DecryptableUri(slide.thumbnailUri!!))
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .let { request ->
                    if (dimens[WIDTH] == 0 || dimens[HEIGHT] == 0) {
                        request.override(getDefaultWidth(), getDefaultHeight())
                    } else {
                        request.override(dimens[WIDTH], dimens[HEIGHT])
                    }
                }
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()

        return if (slide.isInProgress) request else request.apply(RequestOptions.errorOf(R.drawable.ic_missing_thumbnail_picture))
    }

    fun buildPlaceholderGlideRequest(glide: GlideRequests, slide: Slide): GlideRequest<Bitmap> {

        val dimens = dimensDelegate.resourceSize()

        return glide.asBitmap()
                .load(slide.getPlaceholderRes(context.theme))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .let { request ->
                    if (dimens[WIDTH] == 0 || dimens[HEIGHT] == 0) {
                        request.override(getDefaultWidth(), getDefaultHeight())
                    } else {
                        request.override(dimens[WIDTH], dimens[HEIGHT])
                    }
                }
                .fitCenter()
    }
    // endregion

}