package org.thoughtcrime.securesms.components

import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.annotation.UiThread
import androidx.core.os.bundleOf
import org.signal.core.util.dp
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.SlideClickListener
import org.thoughtcrime.securesms.mms.SlidesClickedListener
import org.thoughtcrime.securesms.util.Projection.Corners
import org.thoughtcrime.securesms.util.views.Stub

class ConversationItemThumbnail @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  private var state: ConversationItemThumbnailState
  private var thumbnail: Stub<ThumbnailView>
  private var album: Stub<AlbumThumbnailView>
  private var shade: ImageView
  var footer: Stub<ConversationItemFooter>
    private set
  private var cornerMask: CornerMask
  private var borderless = false
  private var normalBounds: IntArray
  private var gifBounds: IntArray
  private var minimumThumbnailWidth = 0
  private var maximumThumbnailHeight = 0

  init {
    inflate(context, R.layout.conversation_item_thumbnail, this)

    thumbnail = Stub(findViewById(R.id.thumbnail_view_stub))
    album = Stub(findViewById(R.id.album_view_stub))
    shade = findViewById(R.id.conversation_thumbnail_shade)
    footer = Stub(findViewById(R.id.footer_view_stub))
    cornerMask = CornerMask(this)

    var gifWidth = 260.dp

    if (attrs != null) {
      val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.ConversationItemThumbnail, 0, 0)
      normalBounds = intArrayOf(
        typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_minWidth, 0),
        typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_maxWidth, 0),
        typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_minHeight, 0),
        typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_maxHeight, 0)
      )

      gifWidth = typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_gifWidth, gifWidth)

      typedArray.recycle()
    } else {
      normalBounds = intArrayOf(0, 0, 0, 0)
    }

    gifBounds = intArrayOf(
      gifWidth,
      gifWidth,
      1,
      Int.MAX_VALUE
    )

    minimumThumbnailWidth = -1
    maximumThumbnailHeight = -1

    state = ConversationItemThumbnailState()
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    if (!borderless) {
      cornerMask.mask(canvas)
    }
  }

  override fun onSaveInstanceState(): Parcelable? {
    val root = super.onSaveInstanceState()
    return bundleOf(
      STATE_ROOT to root,
      STATE_STATE to state
    )
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    if (state is Bundle && state.containsKey(STATE_STATE)) {
      val root: Parcelable? = state.getParcelableCompat(STATE_ROOT, Parcelable::class.java)
      this.state = state.getParcelableCompat(STATE_STATE, ConversationItemThumbnailState::class.java)!!
      super.onRestoreInstanceState(root)
    } else {
      super.onRestoreInstanceState(state)
    }
  }

  override fun setFocusable(focusable: Boolean) {
    state = state.copy(
      thumbnailViewState = state.thumbnailViewState.copy(focusable = focusable),
      albumViewState = state.albumViewState.copy(focusable = focusable)
    )

    state.applyState(thumbnail, album)
  }

  override fun setClickable(clickable: Boolean) {
    state = state.copy(
      thumbnailViewState = state.thumbnailViewState.copy(clickable = clickable),
      albumViewState = state.albumViewState.copy(clickable = clickable)
    )

    state.applyState(thumbnail, album)
  }

  override fun setOnLongClickListener(l: OnLongClickListener?) {
    state = state.copy(
      thumbnailViewState = state.thumbnailViewState.copy(longClickListener = l),
      albumViewState = state.albumViewState.copy(longClickListener = l)
    )

    state.applyState(thumbnail, album)
  }

  fun hideThumbnailView() {
    state = state.copy(thumbnailViewState = state.thumbnailViewState.copy(alpha = 0f))
    state.thumbnailViewState.applyState(thumbnail)
  }

  fun showThumbnailView() {
    state = state.copy(thumbnailViewState = state.thumbnailViewState.copy(alpha = 1f))
    state.thumbnailViewState.applyState(thumbnail)
  }

  val corners: Corners
    get() = Corners(cornerMask.radii)

  fun showShade(show: Boolean) {
    shade.visibility = if (show) VISIBLE else GONE
    forceLayout()
  }

  fun setCorners(topLeft: Int, topRight: Int, bottomRight: Int, bottomLeft: Int) {
    cornerMask.setRadii(topLeft, topRight, bottomRight, bottomLeft)
    state = state.copy(
      thumbnailViewState = state.thumbnailViewState.copy(
        cornerTopLeft = topLeft,
        cornerTopRight = topRight,
        cornerBottomRight = bottomRight,
        cornerBottomLeft = bottomLeft
      ),
      albumViewState = state.albumViewState.copy(
        cornerTopLeft = topLeft,
        cornerTopRight = topRight,
        cornerBottomRight = bottomRight,
        cornerBottomLeft = bottomLeft
      )
    )

    state.applyState(thumbnail, album)
  }

  fun setMinimumThumbnailWidth(@Px width: Int) {
    minimumThumbnailWidth = width
    state = state.copy(thumbnailViewState = state.thumbnailViewState.copy(minWidth = width))
    state.thumbnailViewState.applyState(thumbnail)
  }

  fun setMaximumThumbnailHeight(@Px height: Int) {
    maximumThumbnailHeight = height
    state = state.copy(thumbnailViewState = state.thumbnailViewState.copy(maxHeight = height))
    state.thumbnailViewState.applyState(thumbnail)
  }

  fun setBorderless(borderless: Boolean) {
    this.borderless = borderless
  }

  @UiThread
  fun setImageResource(
    glideRequests: GlideRequests,
    slides: List<Slide>,
    showControls: Boolean,
    isPreview: Boolean
  ) {
    if (slides.size == 1) {
      val slide = slides[0]

      if (slide.isVideoGif) {
        setThumbnailBounds(gifBounds)
      } else {
        setThumbnailBounds(normalBounds)

        if (minimumThumbnailWidth != -1) {
          state = state.copy(thumbnailViewState = state.thumbnailViewState.copy(minWidth = minimumThumbnailWidth))
        }

        if (maximumThumbnailHeight != -1) {
          state = state.copy(thumbnailViewState = state.thumbnailViewState.copy(maxHeight = maximumThumbnailHeight))
        }
      }

      state = state.copy(
        thumbnailViewState = state.thumbnailViewState.copy(visibility = VISIBLE),
        albumViewState = state.albumViewState.copy(visibility = GONE)
      )

      state.applyState(thumbnail, album)

      val attachment = slides[0].asAttachment()

      thumbnail.get().setImageResource(glideRequests, slides[0], showControls, isPreview, attachment.width, attachment.height)
      touchDelegate = thumbnail.get().touchDelegate
    } else {
      state = state.copy(
        thumbnailViewState = state.thumbnailViewState.copy(visibility = GONE),
        albumViewState = state.albumViewState.copy(visibility = VISIBLE)
      )

      state.applyState(thumbnail, album)
      album.get().setSlides(glideRequests, slides, showControls)
      touchDelegate = album.get().touchDelegate
    }
  }

  fun setConversationColor(@ColorInt color: Int) {
    state = state.copy(albumViewState = state.albumViewState.copy(cellBackgroundColor = color))
    state.albumViewState.applyState(album)
  }

  fun setThumbnailClickListener(listener: SlideClickListener?) {
    state = state.copy(
      thumbnailViewState = state.thumbnailViewState.copy(clickListener = listener),
      albumViewState = state.albumViewState.copy(clickListener = listener)
    )

    state.applyState(thumbnail, album)
  }

  fun setDownloadClickListener(listener: SlidesClickedListener?) {
    state = state.copy(
      thumbnailViewState = state.thumbnailViewState.copy(downloadClickListener = listener),
      albumViewState = state.albumViewState.copy(downloadClickListener = listener)
    )

    state.applyState(thumbnail, album)
  }

  private fun setThumbnailBounds(bounds: IntArray) {
    val (minWidth, maxWidth, minHeight, maxHeight) = bounds
    state = state.copy(
      thumbnailViewState = state.thumbnailViewState.copy(
        minWidth = minWidth,
        maxWidth = maxWidth,
        minHeight = minHeight,
        maxHeight = maxHeight
      )
    )
    state.thumbnailViewState.applyState(thumbnail)
  }

  companion object {
    private const val STATE_ROOT = "state.root"
    private const val STATE_STATE = "state.state"
  }
}
