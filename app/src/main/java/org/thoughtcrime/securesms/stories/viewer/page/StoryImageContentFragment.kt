package org.thoughtcrime.securesms.stories.viewer.page

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.mediapreview.MediaPreviewFragment
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.fragments.requireListener

class StoryImageContentFragment : Fragment(R.layout.stories_image_content_fragment) {

  private var blurState: LoadState = LoadState.INIT
  private var imageState: LoadState = LoadState.INIT

  private val parentViewModel: StoryViewerPageViewModel by viewModels(
    ownerProducer = { requireParentFragment() }
  )

  private lateinit var imageView: ImageView
  private lateinit var blur: ImageView

  private val imageListener = object : StoryCache.Listener {
    override fun onResourceReady(resource: Drawable) {
      imageView.setImageDrawable(resource)
      imageState = LoadState.READY
      notifyListeners()
    }

    override fun onLoadFailed() {
      imageState = LoadState.FAILED
      notifyListeners()
    }
  }

  private val blurListener = object : StoryCache.Listener {
    override fun onResourceReady(resource: Drawable) {
      blur.setImageDrawable(resource)
      blurState = LoadState.READY
      notifyListeners()
    }

    override fun onLoadFailed() {
      blurState = LoadState.FAILED
      notifyListeners()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    imageView = view.findViewById(R.id.image)
    blur = view.findViewById(R.id.blur)

    val storySize = StoryDisplay.getStorySize(resources)
    val blurHash: BlurHash? = requireArguments().getParcelable(BLUR)
    val uri: Uri = requireArguments().getParcelable(URI)!!

    val cacheValue: StoryCache.StoryCacheValue? = parentViewModel.storyCache.getFromCache(uri)
    if (cacheValue != null) {
      loadViaCache(cacheValue)
    } else {
      loadViaGlide(blurHash, storySize)
    }
  }

  private fun loadViaCache(cacheValue: StoryCache.StoryCacheValue) {
    Log.d(TAG, "Attachment in cache. Loading via cache...")
    val blurTarget = cacheValue.blurTarget
    if (blurTarget != null) {
      blurTarget.addListener(blurListener)
      viewLifecycleOwner.lifecycle.addObserver(OnDestroy { blurTarget.removeListener(blurListener) })
    } else {
      blurState = LoadState.FAILED
      notifyListeners()
    }

    val imageTarget = cacheValue.imageTarget
    imageTarget.addListener(imageListener)
    viewLifecycleOwner.lifecycle.addObserver(OnDestroy { imageTarget.removeListener(blurListener) })
  }

  private fun loadViaGlide(blurHash: BlurHash?, storySize: StoryDisplay.Size) {
    Log.d(TAG, "Attachment not in cache. Loading via glide...")
    if (blurHash != null) {
      GlideApp.with(blur)
        .load(blurHash)
        .override(storySize.width, storySize.height)
        .addListener(object : RequestListener<Drawable> {
          override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
            blurState = LoadState.FAILED
            notifyListeners()
            return false
          }

          override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
            blurState = LoadState.READY
            notifyListeners()
            return false
          }
        })
        .into(blur)
    } else {
      blurState = LoadState.FAILED
      notifyListeners()
    }

    GlideApp.with(imageView)
      .load(DecryptableStreamUriLoader.DecryptableUri(requireArguments().getParcelable(URI)!!))
      .override(storySize.width, storySize.height)
      .addListener(object : RequestListener<Drawable> {
        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
          imageState = LoadState.FAILED
          notifyListeners()
          return false
        }

        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
          imageState = LoadState.READY
          notifyListeners()
          return false
        }
      })
      .into(imageView)
  }

  private fun notifyListeners() {
    if (isDetached) {
      Log.w(TAG, "Fragment is detached, dropping notify call.")
      return
    }

    if (blurState != LoadState.INIT && imageState != LoadState.INIT) {
      if (imageState == LoadState.FAILED) {
        requireListener<MediaPreviewFragment.Events>().mediaNotAvailable()
      } else {
        requireListener<MediaPreviewFragment.Events>().onMediaReady()
      }
    }
  }

  private inner class OnDestroy(private val onDestroy: () -> Unit) : DefaultLifecycleObserver {
    override fun onDestroy(owner: LifecycleOwner) {
      onDestroy()
    }
  }

  enum class LoadState {
    INIT,
    READY,
    FAILED
  }

  companion object {

    private val TAG = Log.tag(StoryImageContentFragment::class.java)

    private const val URI = MediaPreviewFragment.DATA_URI
    private const val BLUR = "blur_hash"

    fun create(attachment: Attachment): StoryImageContentFragment {
      return StoryImageContentFragment().apply {
        arguments = Bundle().apply {
          putParcelable(URI, attachment.uri!!)
          putParcelable(BLUR, attachment.blurHash)
        }
      }
    }
  }
}
