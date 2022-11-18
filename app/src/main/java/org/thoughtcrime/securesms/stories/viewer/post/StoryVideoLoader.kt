package org.thoughtcrime.securesms.stories.viewer.post

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.mms.VideoSlide
import org.thoughtcrime.securesms.video.VideoPlayer

/**
 * Render logic for story video posts
 */
class StoryVideoLoader(
  private val fragment: StoryPostFragment,
  private val videoPost: StoryPostState.VideoPost,
  private val videoPlayer: VideoPlayer,
  private val callback: StoryPostFragment.Callback,
  private val blurLoader: StoryBlurLoader
) : DefaultLifecycleObserver {

  companion object {
    private val TAG = Log.tag(StoryVideoLoader::class.java)
  }

  fun load() {
    fragment.viewLifecycleOwner.lifecycle.addObserver(this)
    videoPlayer.setVideoSource(VideoSlide(fragment.requireContext(), videoPost.videoUri, videoPost.size, false), false, TAG, videoPost.clipStart.inWholeMilliseconds, videoPost.clipEnd.inWholeMilliseconds)
    videoPlayer.hideControls()
    videoPlayer.setKeepContentOnPlayerReset(false)
    blurLoader.load()
  }

  fun clear() {
    fragment.viewLifecycleOwner.lifecycle.removeObserver(this)
    videoPlayer.stop()
    blurLoader.clear()
  }

  override fun onResume(lifecycleOwner: LifecycleOwner) {
    callback.getVideoControlsDelegate()?.attachPlayer(videoPost.videoUri, videoPlayer, false)
  }

  override fun onPause(lifecycleOwner: LifecycleOwner) {
    callback.getVideoControlsDelegate()?.detachPlayer()
    videoPlayer.pause()
  }
}
