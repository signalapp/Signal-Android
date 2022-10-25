package org.thoughtcrime.securesms.mediapreview

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ui.PlayerControlView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.MediaUtil

/**
 * The bottom bar for the media preview. This includes the standard seek bar as well as playback controls,
 * but adds forward and share buttons as well as a recyclerview that can be populated with a rail of thumbnails.
 */
class MediaPreviewPlayerControlView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  playbackAttrs: AttributeSet? = null
) : PlayerControlView(context, attrs, defStyleAttr, playbackAttrs) {

  val recyclerView: RecyclerView = findViewById(R.id.media_preview_album_rail)
  private val durationBar: LinearLayout = findViewById(R.id.exo_duration_viewgroup)
  private val videoControls: LinearLayout = findViewById(R.id.exo_button_viewgroup)
  private val shareButton: ImageButton = findViewById(R.id.exo_share)
  private val forwardButton: ImageButton = findViewById(R.id.exo_forward)

  enum class MediaMode {
    IMAGE, VIDEO;

    companion object {
      @JvmStatic
      fun fromString(contentType: String): MediaMode {
        if (MediaUtil.isVideo(contentType)) return VIDEO
        if (MediaUtil.isImageType(contentType)) return IMAGE
        throw IllegalArgumentException("Unknown content type: $contentType")
      }
    }
  }

  init {
    setShowPreviousButton(false)
    setShowNextButton(false)
    showShuffleButton = false
    showVrButton = false
    showTimeoutMs = -1
  }

  fun setVisibility(mediaMode: MediaMode) {
    durationBar.visibility = if (mediaMode == MediaMode.VIDEO) VISIBLE else GONE
    videoControls.visibility = if (mediaMode == MediaMode.VIDEO) VISIBLE else INVISIBLE
  }

  fun setShareButtonListener(listener: OnClickListener?) = shareButton.setOnClickListener(listener)

  fun setForwardButtonListener(listener: OnClickListener?) = forwardButton.setOnClickListener(listener)
}
