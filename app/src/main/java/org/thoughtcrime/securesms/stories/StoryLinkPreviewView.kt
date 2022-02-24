package org.thoughtcrime.securesms.stories

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import okhttp3.HttpUrl
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.OutlinedThumbnailView
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.util.Util
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
  private val image: OutlinedThumbnailView = findViewById(R.id.link_preview_image)
  private val title: TextView = findViewById(R.id.link_preview_title)
  private val url: TextView = findViewById(R.id.link_preview_url)

  fun bind(linkPreviewState: LinkPreviewViewModel.LinkPreviewState, hiddenVisibility: Int = View.INVISIBLE) {
    if (linkPreviewState.linkPreview.isPresent) {
      visibility = View.VISIBLE
      isClickable = true

      val linkPreview: LinkPreview = linkPreviewState.linkPreview.get()

      val corners = DimensionUnit.DP.toPixels(18f).toInt()
      image.setCorners(corners, corners, corners, corners)

      val imageSlide: ImageSlide? = linkPreview.thumbnail.transform { ImageSlide(image.context, it) }.orNull()
      if (imageSlide != null) {
        image.setImageResource(GlideApp.with(image), imageSlide, false, false)
        image.visible = true
      } else {
        image.visible = false
      }

      title.text = linkPreview.title

      formatUrl(linkPreview)
    } else {
      visibility = hiddenVisibility
      isClickable = false
    }
  }

  private fun formatUrl(linkPreview: LinkPreview) {
    var domain: String? = null

    if (!Util.isEmpty(linkPreview.url)) {
      val url = HttpUrl.parse(linkPreview.url)
      if (url != null) {
        domain = url.topPrivateDomain()
      }
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
