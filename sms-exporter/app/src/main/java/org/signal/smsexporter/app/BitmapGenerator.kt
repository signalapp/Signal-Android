package org.signal.smsexporter.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.applyCanvas
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Random

object BitmapGenerator {

  private val colors = listOf(
    Color.BLACK,
    Color.BLUE,
    Color.GRAY,
    Color.GREEN,
    Color.RED,
    Color.CYAN
  )

  fun getStream(): InputStream {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    bitmap.applyCanvas {
      val random = Random()
      drawColor(colors[random.nextInt(colors.size - 1)])
    }

    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
    val data = out.toByteArray()

    return ByteArrayInputStream(data)
  }
}
