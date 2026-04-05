package org.thoughtcrime.securesms.glide.cache

import com.bumptech.glide.load.EncodeStrategy
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceEncoder
import com.bumptech.glide.load.engine.Resource
import org.signal.apng.ApngDecoder
import org.signal.core.util.logging.Log.tag
import org.signal.core.util.logging.Log.w
import java.io.File
import java.io.IOException

internal class EncryptedApngResourceEncoder(private val secret: ByteArray) : EncryptedCoder(), ResourceEncoder<ApngDecoder> {
  override fun getEncodeStrategy(options: Options): EncodeStrategy {
    return EncodeStrategy.SOURCE
  }

  override fun encode(data: Resource<ApngDecoder>, file: File, options: Options): Boolean {
    try {
      val input = data.get().streamFactory()
      val output = createEncryptedOutputStream(secret, file)

      input.copyTo(output)
      input.close()

      return true
    } catch (e: IOException) {
      w(TAG, e)
    }
    return false
  }

  companion object {
    private val TAG = tag(EncryptedApngResourceEncoder::class.java)
  }
}
