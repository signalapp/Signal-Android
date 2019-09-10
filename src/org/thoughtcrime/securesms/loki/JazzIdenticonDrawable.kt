package org.thoughtcrime.securesms.loki

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.*

class JazzIdenticonDrawable(width: Int, height: Int, hash: Long) : IdenticonDrawable(width, height, hash) {

  constructor(width: Int, height: Int, hashString: String): this(width, height, 0) {
    val hexRegex = Regex("^[0-9A-Fa-f]+\$")
    if (hashString.length >= 12 && hashString.matches(hexRegex)) {
      hash = hashString.substring(0 until 12).toLong(16)
    }
  }

  companion object {
    var colors = listOf(
            "#01888c", // teal
            "#fc7500", // bright orange
            "#034f5d", // dark teal
            "#E784BA", // light pink
            "#81C8B6", // bright green
            "#c7144c", // raspberry
            "#f3c100", // goldenrod
            "#1598f2", // lightning blue
            "#2465e1", // sail blue
            "#f19e02"  // gold
    ).map{ Color.parseColor(it) }
  }

  private var generator: RNG = RNG(hash)
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

  // Settings
  private val wobble: Float = 30f
  private val shapeCount = 4

  init {
    invalidateBitmap()
  }

  override fun onSetHash(newHash: Long) {
    super.onSetHash(newHash)
    generator = RNG(newHash)
    invalidateBitmap()
  }

  override fun drawBitmap(canvas: Canvas) {
    generator.reset()

    val newColors = hueShift(colors)
    val shuffled = shuffleList(newColors)

    canvas.drawColor(shuffled[0])
    for (i in 0 until shapeCount) {
      drawSquare(canvas, shuffled[i + 1], i, shapeCount - 1)
    }
  }

  private fun drawSquare(canvas: Canvas, color: Int, index: Int, total: Int) {
    val size = min(canvas.width, canvas.height)
    val center = (size / 2).toFloat()
    val firstRotation = generator.nextFloat()
    val angle = PI * 2 * firstRotation

    val a = size / total.toFloat()
    val b = generator.nextFloat()
    val c = index.toFloat() * a
    val velocity = a * b + c

    val tx = cos(angle) * velocity
    val ty = sin(angle) * velocity

    // Third random is a shape rotation on top of all that
    val secondRotation = generator.nextFloat()
    val rotation = (firstRotation * 360f) + (secondRotation * 180f)

    // Paint it!
    canvas.save()

    paint.color = color
    canvas.translate(tx.toFloat(), ty.toFloat())
    canvas.rotate(rotation.round(1), center, center)
    canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)

    canvas.restore()
  }

  private fun hueShift(colors: List<Int>): List<Int> {
    val amount = generator.nextFloat() * 30 - wobble / 2

    return colors.map { color ->
      val red = Color.red(color)
      val green = Color.green(color)
      val blue = Color.blue(color)

      val hsv = FloatArray(3)
      Color.RGBToHSV(red, green, blue, hsv)

      // Normalise between 0 and 360
      var newHue = hsv[0] + round(amount)
      if (newHue < 0) { newHue += 360 }
      if (newHue > 360) { newHue -= 360 }

      hsv[0] = newHue
      Color.HSVToColor(hsv)
    }
  }

  private fun <T> shuffleList(list: List<T>): List<T> {
    var currentIndex = list.count()
    val newList = list.toMutableList()
    while (currentIndex > 0) {
      val randomIndex = generator.next().toInt() % currentIndex
      currentIndex -= 1

      // Swap
      val temp = newList[currentIndex]
      newList[currentIndex] = newList[randomIndex]
      newList[randomIndex] = temp
    }

    return newList
  }
}

private fun Float.round(decimals: Int): Float {
  var multiplier = 1f
  repeat(decimals) { multiplier *= 10 }
  return round(this * multiplier) / multiplier
}