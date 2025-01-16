package org.thoughtcrime.securesms.mediasend.v2

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaTransform
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.FileInputStream
import java.nio.ByteBuffer

class VideoMuteTransform : MediaTransform {

  @WorkerThread
  override fun transform(context: Context, media: Media): Media {
    try {
      val oldUri = media.uri
      val mutedVideoInputStream = getMutedVideoStream(context,oldUri) ?: return media

      val mutedVideoUri = BlobProvider.getInstance()
        .forData(mutedVideoInputStream, mutedVideoInputStream.channel.size())
        .withMimeType(MediaUtil.VIDEO_MP4)
        .createForSingleSessionOnDisk(context)
      return Media(
        mutedVideoUri,
        media.contentType,
        media.date,
        media.width,
        media.height,
        media.size,
        media.duration,
        media.isBorderless,
        media.isVideoGif,
        media.bucketId,
        media.caption,
        media.transformProperties,
        media.fileName
      )
    } catch (e: Exception) {
      e.printStackTrace()
      return media
    }
  }

  @SuppressLint("WrongConstant")
  private fun getMutedVideoStream(context: Context, oldUri: Uri): FileInputStream? {
    try {
      val resolver = context.contentResolver
      val inputDescriptor = resolver.openFileDescriptor(oldUri, "r") ?: return null

      val extractor = MediaExtractor()
      extractor.setDataSource(inputDescriptor.fileDescriptor)

      val tempFile = BlobProvider().forNonAutoEncryptingSingleSessionOnDisk(context)
      val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

      var videoTrackIndex = -1
      var rotationDegrees = 0

      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

        if (mime.startsWith("video/")) {
          extractor.selectTrack(i)
          videoTrackIndex = muxer.addTrack(format)

          if (format.containsKey("rotation-degrees")) {
            rotationDegrees = format.getInteger("rotation-degrees")
          }
        }
      }

      if (videoTrackIndex == -1) {
        extractor.release()
        muxer.release()
        return null
      }

      muxer.setOrientationHint(rotationDegrees)
      muxer.start()

      val buffer = ByteBuffer.allocate(1024 * 1024)
      val bufferInfo = MediaCodec.BufferInfo()

      while (true) {
        bufferInfo.offset = 0
        bufferInfo.size = extractor.readSampleData(buffer, 0)

        if (bufferInfo.size < 0) {
          bufferInfo.size = 0
          break
        }

        bufferInfo.presentationTimeUs = extractor.sampleTime
        bufferInfo.flags = extractor.sampleFlags
        muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
        extractor.advance()
      }

      muxer.stop()
      muxer.release()
      extractor.release()

      return FileInputStream(tempFile)
    } catch (e: Exception) {
      e.printStackTrace()
      return null
    }
  }
}
