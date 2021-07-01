package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import kotlinx.android.synthetic.main.thumbnail_view.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.utilities.Util.equals
import org.session.libsignal.utilities.ListenableFuture
import org.session.libsignal.utilities.SettableFuture
import org.thoughtcrime.securesms.components.GlideBitmapListeningTarget
import org.thoughtcrime.securesms.components.GlideDrawableListeningTarget
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.*
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri

open class KThumbnailView: FrameLayout {

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
    val loadIndicator: View by lazy { thumbnail_load_indicator }

    private val dimensDelegate = ThumbnailDimensDelegate()

    private var slide: Slide? = null
    private var radius: Int = 0

    private fun initialize(attrs: AttributeSet?) {
        inflate(context, R.layout.thumbnail_view, this)
        if (attrs != null) {
            val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.ThumbnailView, 0, 0)

            dimensDelegate.setBounds(typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_minWidth, 0),
                    typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_minHeight, 0),
                    typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_maxWidth, 0),
                    typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_maxHeight, 0))

            radius = typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_thumbnail_radius, 0)

            typedArray.recycle()
        }
    }

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
    fun setImageResource(glide: GlideRequests, slide: Slide, isPreview: Boolean, mms: MmsMessageRecord): ListenableFuture<Boolean> {
        return setImageResource(glide, slide, isPreview, 0, 0, mms)
    }

    fun setImageResource(glide: GlideRequests, slide: Slide,
                         isPreview: Boolean, naturalWidth: Int,
                         naturalHeight: Int, mms: MmsMessageRecord): ListenableFuture<Boolean> {

        val currentSlide = this.slide

        playOverlay.isVisible = (slide.thumbnailUri != null && slide.hasPlayOverlay() &&
                (slide.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_DONE || isPreview))

        if (equals(currentSlide, slide)) {
            // don't re-load slide
            return SettableFuture(false)
        }


        if (currentSlide != null && currentSlide.fastPreflightId != null && currentSlide.fastPreflightId == slide.fastPreflightId) {
            // not reloading slide for fast preflight
            this.slide = slide
        }

        this.slide = slide

        loadIndicator.isVisible = slide.isInProgress && !mms.isFailed

        dimensDelegate.setDimens(naturalWidth, naturalHeight)
        invalidate()

        val result = SettableFuture<Boolean>()

        when {
            slide.thumbnailUri != null -> {
                buildThumbnailGlideRequest(glide, slide).into(GlideDrawableListeningTarget(image, result))
            }
            slide.hasPlaceholder() -> {
                buildPlaceholderGlideRequest(glide, slide).into(GlideBitmapListeningTarget(image, result))
            }
            else -> {
                glide.clear(image)
                result.set(false)
            }
        }
        return result
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

    open fun clear(glideRequests: GlideRequests) {
        glideRequests.clear(image)
        slide = null
    }

    fun setImageResource(glideRequests: GlideRequests, uri: Uri): ListenableFuture<Boolean> {
        val future = SettableFuture<Boolean>()

        var request: GlideRequest<Drawable> = glideRequests.load(DecryptableUri(uri))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .transition(DrawableTransitionOptions.withCrossFade())

        request = if (radius > 0) {
            request.transforms(CenterCrop(), RoundedCorners(radius))
        } else {
            request.transforms(CenterCrop())
        }

        request.into(GlideDrawableListeningTarget(image, future))

        return future
    }

    // endregion

}