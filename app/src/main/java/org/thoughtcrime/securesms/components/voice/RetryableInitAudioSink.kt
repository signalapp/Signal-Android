package org.thoughtcrime.securesms.components.voice

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import org.signal.core.util.logging.Log
import java.nio.ByteBuffer

/**
 * Certain devices, including the incredibly popular Samsung A51, often fail to create a proper audio sink when switching from "media" mode to "communication" mode.
 * It does eventually recover, but it needs to be given ample opportunity to.
 * This class wraps the final DefaultAudioSink to provide exactly that functionality.
 */
@OptIn(UnstableApi::class)
class RetryableInitAudioSink(
  context: Context,
  enableFloatOutput: Boolean,
  enableAudioTrackPlaybackParams: Boolean,
  val delegate: AudioSink = DefaultAudioSink.Builder(context)
    .setEnableFloatOutput(enableFloatOutput)
    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
    .build()
) : AudioSink by delegate {

  private var retriesLeft = INITIAL_RETRY_COUNT

  override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
    return try {
      val bufferHandled = delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
      if (bufferHandled) {
        retriesLeft = INITIAL_RETRY_COUNT
      }
      bufferHandled
    } catch (e: AudioSink.InitializationException) {
      Log.w(TAG, "Could not handle this buffer due to an initialization exception. $retriesLeft retries remaining.", e)
      if (retriesLeft > 0) {
        retriesLeft--
        false
      } else {
        throw e
      }
    }
  }

  companion object {
    private val TAG = Log.tag(RetryableInitAudioSink::class.java)
    const val INITIAL_RETRY_COUNT = 5
  }
}
