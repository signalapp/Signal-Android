package org.thoughtcrime.securesms.stories

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ThumbnailView
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture
import org.thoughtcrime.securesms.util.concurrent.SettableFuture
import org.thoughtcrime.securesms.util.views.Stub
import org.thoughtcrime.securesms.util.visible
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

class StoryLinkPreviewView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

  init {
    inflate(context, R.layout.stories_text_post_link_preview, this)
  }

  private val close: View = findViewById(R.id.link_preview_close)
  private val smallImage: ThumbnailView = findViewById(R.id.link_preview_image)
  private val largeImage: ThumbnailView = findViewById(R.id.link_preview_large)
  private val title: TextView = findViewById(R.id.link_preview_title)
  private val url: TextView = findViewById(R.id.link_preview_url)
  private val description: TextView = findViewById(R.id.link_preview_description)
  private val fallbackIcon: ImageView = findViewById(R.id.link_preview_fallback_icon)
  private val loadingSpinner: Stub<View> = Stub(findViewById(R.id.loading_spinner))

  private fun getThumbnailTarget(useLargeThumbnail: Boolean): ThumbnailView {
    return if (useLargeThumbnail) largeImage else smallImage
  }

  fun getThumbnailViewWidth(useLargeThumbnail: Boolean): Int {
    return getThumbnailTarget(useLargeThumbnail).measuredWidth
  }

  fun getThumbnailViewHeight(useLargeThumbnail: Boolean): Int {
    return getThumbnailTarget(useLargeThumbnail).measuredHeight
  }

  fun setThumbnailDrawable(drawable: Drawable?, useLargeThumbnail: Boolean) {
    val image = getThumbnailTarget(useLargeThumbnail)
    image.setImageDrawable(GlideApp.with(this), drawable)
  }

  fun bind(linkPreview: LinkPreview?, hiddenVisibility: Int = View.INVISIBLE, useLargeThumbnail: Boolean, loadThumbnail: Boolean = true): ListenableFuture<Boolean> {
    var future: ListenableFuture<Boolean>? = null

    if (linkPreview != null) {
      visibility = View.VISIBLE
      isClickable = true

      val image = getThumbnailTarget(useLargeThumbnail)
      val notImage = getThumbnailTarget(!useLargeThumbnail)

      notImage.visible = false

      val imageSlide: Slide? = linkPreview.thumbnail.map { ImageSlide(context, it) }.orElse(null)
      if (imageSlide != null) {
        if (loadThumbnail) {
          future = image.setImageResource(
            GlideApp.with(this),
            imageSlide,
            false,
            false
          )
        }

        image.visible = true
        fallbackIcon.visible = false
      } else {
        image.visible = false
        fallbackIcon.visible = true
      }

      title.text = linkPreview.title
      description.text = linkPreview.description
      description.visible = linkPreview.description.isNotEmpty()

      formatUrl(linkPreview)
    } else {
      visibility = hiddenVisibility
      isClickable = false
    }

    return future ?: SettableFuture(false)
  }

  fun bind(linkPreviewState: LinkPreviewViewModel.LinkPreviewState, hiddenVisibility: Int = View.INVISIBLE, useLargeThumbnail: Boolean) {
    val linkPreview: LinkPreview? = linkPreviewState.linkPreview.orElseGet {
      linkPreviewState.activeUrlForError?.let {
        LinkPreview(it, LinkPreviewUtil.getTopLevelDomain(it) ?: it, null, -1L, null)
      }
    }

    bind(linkPreview, hiddenVisibility, useLargeThumbnail)

    loadingSpinner.get().visible = linkPreviewState.isLoading
    if (linkPreviewState.isLoading) {
      visible = true
    }
  }

  private fun formatUrl(linkPreview: LinkPreview) {
    val domain: String? = LinkPreviewUtil.getTopLevelDomain(linkPreview.url)

    if (linkPreview.title == domain) {
      url.visibility = View.GONE
      return
    }

    if (domain != null && linkPreview.date > 0) {
      url.text = url.context.getString(R.string.LinkPreviewView_domain_date, domain, formatDate(linkPreview.date))
      url.visibility = View.VISIBLE
    } else if (domain != null) {
      url.text = domain
      url.visibility = View.VISIBLE
    } else if (linkPreview.date > 0) {
      url.text = formatDate(linkPreview.date)
      url.visibility = View.VISIBLE
    } else {
      url.visibility = View.GONE
    }
  }

  fun setOnCloseClickListener(onClickListener: OnClickListener?) {
    close.setOnClickListener(onClickListener)
  }

  fun setCanClose(canClose: Boolean) {
    close.visible = canClose
  }

  private fun formatDate(date: Long): String? {
    val dateFormat: DateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return dateFormat.format(date)
  }
}
